package io.mopl.domain.content.repository.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.content.document.ContentDocument;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.global.config.BaseIntegrationTest;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import io.mopl.global.util.InitialUtils;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_OPENSEARCH_INTEGRATION_TESTS", matches = "true")
class ContentSearchInitialPopularityIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private ContentSearchRepository contentSearchRepository;

  @Autowired
  private ElasticsearchOperations elasticsearchOperations;

  private IndexOperations indexOperations;
  private UUID mostPopularId;
  private UUID secondPopularId;
  private UUID leastPopularId;

  @BeforeEach
  void setUp() {
    indexOperations = elasticsearchOperations.indexOps(ContentDocument.class);
    if (indexOperations.exists()) {
      indexOperations.delete();
    }
    indexOperations.createWithMapping();

    mostPopularId = UUID.randomUUID();
    secondPopularId = UUID.randomUUID();
    leastPopularId = UUID.randomUUID();
    contentSearchRepository.saveAll(List.of(
        document(mostPopularId, "기생충", 7, 2.0),
        document(secondPopularId, "괴물", 5, 4.8),
        document(leastPopularId, "국제시장", 5, 3.0)
    ));
    indexOperations.refresh();
  }

  @AfterEach
  void tearDown() {
    if (indexOperations != null && indexOperations.exists()) {
      indexOperations.delete();
    }
  }

  @Test
  void searchesSingleInitialAndSortsByReviewCountThenRatingWithCursor() {
    CursorResponse<UUID> firstPage = contentSearchRepository.searchContentIdsByCursor(
        null,
        "ㄱ",
        null,
        null,
        null,
        2,
        "watcherCount",
        SortDirection.DESCENDING
    );
    CursorResponse<UUID> secondPage = contentSearchRepository.searchContentIdsByCursor(
        null,
        "ㄱ",
        null,
        firstPage.nextCursor(),
        firstPage.nextIdAfter(),
        2,
        "watcherCount",
        SortDirection.DESCENDING
    );

    assertThat(firstPage.data()).containsExactly(mostPopularId, secondPopularId);
    assertThat(firstPage.nextCursor()).isEqualTo("5|4.8");
    assertThat(firstPage.totalCount()).isEqualTo(3L);
    assertThat(secondPage.data()).containsExactly(leastPopularId);
  }

  private ContentDocument document(
      UUID contentId,
      String title,
      int reviewCount,
      double averageRating
  ) {
    return ContentDocument.builder()
        .id(contentId)
        .title(title)
        .description("한국 콘텐츠")
        .initials(InitialUtils.extractInitial(title))
        .type(ContentType.MOVIE.getValue())
        .tags(Set.of("영화"))
        .createdAt(Instant.parse("2026-07-25T00:00:00Z"))
        .averageRating(averageRating)
        .reviewCount(reviewCount)
        .build();
  }
}
