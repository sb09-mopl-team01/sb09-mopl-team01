package io.mopl.domain.content.repository.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.content.document.ContentDocument;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.global.config.BaseIntegrationTest;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
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
class ContentSearchRepositoryIntegrationTest extends BaseIntegrationTest {

  @Autowired
  private ContentSearchRepository contentSearchRepository;

  @Autowired
  private ElasticsearchOperations elasticsearchOperations;

  private IndexOperations indexOperations;

  @BeforeEach
  void setUp() {
    indexOperations = elasticsearchOperations.indexOps(ContentDocument.class);
    if (indexOperations.exists()) {
      indexOperations.delete();
    }
    indexOperations.createWithMapping();

    contentSearchRepository.saveAll(List.of(
        document("인터스텔라", "우주 탐험 콘텐츠", ContentType.MOVIE, Set.of("SF"),
            "2026-07-20T00:00:00Z", 4.5),
        document("한국 드라마", "가족의 이야기 콘텐츠", ContentType.TV_SERIES, Set.of("드라마"),
            "2026-07-21T00:00:00Z", 4.9),
        document("축구 경기", "유럽 축구 콘텐츠", ContentType.SPORT, Set.of("스포츠", "축구"),
            "2026-07-22T00:00:00Z", 4.0)
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
  void searchesKoreanPartialKeywordAcrossTitleAndDescription() {
    CursorResponse<UUID> titleResult = contentSearchRepository.searchContentIdsByCursor(
        null, "스텔", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    );
    CursorResponse<UUID> descriptionResult = contentSearchRepository.searchContentIdsByCursor(
        null, "가족", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    );

    assertThat(titleResult.data()).hasSize(1);
    assertThat(descriptionResult.data()).hasSize(1);
  }

  @Test
  void appliesTypeAndTagFiltersTogether() {
    CursorResponse<UUID> result = contentSearchRepository.searchContentIdsByCursor(
        ContentType.TV_SERIES,
        "콘텐츠",
        List.of("드라마"),
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(result.data()).hasSize(1);
    assertThat(result.totalCount()).isEqualTo(1L);
  }

  @Test
  void keepsCursorPaginationOrderForCreatedAtAndRateSorts() {
    CursorResponse<UUID> firstPage = contentSearchRepository.searchContentIdsByCursor(
        null, "콘텐츠", null, null, null, 1, "createdAt", SortDirection.DESCENDING
    );
    CursorResponse<UUID> secondPage = contentSearchRepository.searchContentIdsByCursor(
        null,
        "콘텐츠",
        null,
        firstPage.nextCursor(),
        firstPage.nextIdAfter(),
        1,
        "createdAt",
        SortDirection.DESCENDING
    );
    CursorResponse<UUID> rateResult = contentSearchRepository.searchContentIdsByCursor(
        null, "콘텐츠", null, null, null, 3, "rate", SortDirection.DESCENDING
    );

    assertThat(firstPage.hasNext()).isTrue();
    assertThat(firstPage.data()).doesNotContainAnyElementsOf(secondPage.data());
    assertThat(firstPage.totalCount()).isEqualTo(3L);
    assertThat(rateResult.data()).hasSize(3);
  }

  private ContentDocument document(
      String title,
      String description,
      ContentType type,
      Set<String> tags,
      String createdAt,
      double averageRating
  ) {
    return ContentDocument.builder()
        .id(UUID.randomUUID())
        .title(title)
        .description(description)
        .type(type.getValue())
        .tags(tags)
        .createdAt(Instant.parse(createdAt))
        .averageRating(averageRating)
        .build();
  }
}
