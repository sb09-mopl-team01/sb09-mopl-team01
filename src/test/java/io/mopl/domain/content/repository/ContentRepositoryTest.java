package io.mopl.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(io.mopl.global.config.AppConfig.class)
@ActiveProfiles("test")
class ContentRepositoryTest {

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  @DisplayName("수동 등록 콘텐츠를 저장하고 중복 태그를 제거한다")
  void saveManualContent() {
    Content content = Content.createManual(
        ContentType.MOVIE,
        "인사이드 아웃 2",
        "감정 캐릭터가 등장하는 애니메이션 영화",
        "https://image.example.com/inside-out-2.jpg",
        List.of("애니메이션", "가족", "애니메이션")
    );

    Content savedContent = contentRepository.saveAndFlush(content);
    entityManager.clear();

    Content foundContent = contentRepository.findById(savedContent.getId()).orElseThrow();

    assertThat(foundContent.getSource()).isEqualTo(ContentSource.MANUAL);
    assertThat(foundContent.getExternalId()).isNull();
    assertThat(foundContent.getTags()).containsExactlyInAnyOrder("애니메이션", "가족");
  }

  @Test
  @DisplayName("외부 콘텐츠를 source와 externalId 조합으로 조회한다")
  void findExternalContentBySourceAndExternalId() {
    Instant syncedAt = Instant.parse("2026-06-25T00:00:00Z");
    Content content = Content.createExternal(
        ContentType.TV_SERIES,
        "드라마",
        "TMDB에서 수집한 TV 시리즈",
        null,
        ContentSource.TMDB,
        "12345",
        syncedAt,
        List.of("드라마")
    );

    contentRepository.saveAndFlush(content);
    entityManager.clear();

    assertThat(contentRepository.existsBySourceAndExternalId(ContentSource.TMDB, "12345"))
        .isTrue();
    assertThat(contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "12345"))
        .isPresent()
        .get()
        .extracting(Content::getLastSyncedAt)
        .isEqualTo(syncedAt);
  }

  @Test
  @DisplayName("콘텐츠 타입은 Swagger enum 값으로 저장한다")
  void saveContentTypeAsApiValue() {
    Content content = Content.createManual(
        ContentType.TV_SERIES,
        "TV 시리즈",
        "tvSeries 타입 저장 확인용 콘텐츠",
        null,
        List.of("드라마")
    );

    Content savedContent = contentRepository.saveAndFlush(content);

    Object typeValue = entityManager.getEntityManager()
        .createNativeQuery("select type from contents where id = ?")
        .setParameter(1, savedContent.getId())
        .getSingleResult();

    assertThat(typeValue).isEqualTo("tvSeries");
  }

  @Test
  @DisplayName("외부 콘텐츠는 externalId가 필수이고 수동 콘텐츠는 externalId를 가질 수 없다")
  void validateExternalIdPolicy() {
    assertThatThrownBy(() -> Content.createExternal(
        ContentType.SPORT,
        "경기",
        "스포츠 경기",
        null,
        ContentSource.THE_SPORTS_DB,
        null,
        Instant.parse("2026-06-25T00:00:00Z"),
        List.of("스포츠")
    )).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("externalId");

    assertThatThrownBy(() -> Content.createExternal(
        ContentType.SPORT,
        "경기",
        "스포츠 경기",
        null,
        ContentSource.MANUAL,
        "manual-id",
        Instant.parse("2026-06-25T00:00:00Z"),
        List.of("스포츠")
    )).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MANUAL");
  }

  @Test
  @DisplayName("콘텐츠 태그는 하나 이상 필요하다")
  void requireAtLeastOneTag() {
    assertThatThrownBy(() -> Content.createManual(
        ContentType.MOVIE,
        "영화",
        "태그 없는 영화",
        null,
        List.of()
    )).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("태그");
  }

  @Test
  @DisplayName("콘텐츠 태그는 공백을 제거하고 50자 이하로 저장한다")
  void validateTagConstraint() {
    Content content = Content.createManual(
        ContentType.MOVIE,
        "영화",
        "태그 제약 확인용 영화",
        null,
        List.of(" 액션 ")
    );

    Content savedContent = contentRepository.saveAndFlush(content);
    entityManager.clear();

    Content foundContent = contentRepository.findById(savedContent.getId()).orElseThrow();

    assertThat(foundContent.getTags()).containsExactly("액션");
    assertThatThrownBy(() -> Content.createManual(
        ContentType.MOVIE,
        "영화",
        "태그 길이 검증용 영화",
        null,
        List.of("a".repeat(51))
    )).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("50자");
  }

  @Test
  @DisplayName("타입, 키워드, 태그 조건으로 콘텐츠 목록을 조회한다")
  void findContentsByCursorWithFilters() {
    Content movie = contentRepository.saveAndFlush(Content.createManual(
        ContentType.MOVIE,
        "인터스텔라",
        "우주 탐사를 다룬 영화",
        null,
        List.of("SF", "우주")
    ));
    contentRepository.saveAndFlush(Content.createManual(
        ContentType.TV_SERIES,
        "우주 드라마",
        "우주 배경 TV 시리즈",
        null,
        List.of("SF")
    ));
    entityManager.clear();

    CursorResponse<Content> result = contentRepository.findContentsByCursor(
        ContentType.MOVIE,
        "우주",
        List.of("SF"),
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(result.data())
        .extracting(Content::getId)
        .containsExactly(movie.getId());
    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(result.hasNext()).isFalse();
  }

  @Test
  @DisplayName("평점순으로 콘텐츠 목록을 조회한다")
  void findContentsByRateSort() {
    Content lowRatedContent = Content.createManual(
        ContentType.MOVIE,
        "낮은 평점 영화",
        "평점순 정렬 확인용 영화",
        null,
        List.of("영화")
    );
    lowRatedContent.updateReviewStats(2.0, 1);
    Content highRatedContent = Content.createManual(
        ContentType.MOVIE,
        "높은 평점 영화",
        "평점순 정렬 확인용 영화",
        null,
        List.of("영화")
    );
    highRatedContent.updateReviewStats(4.5, 3);

    contentRepository.saveAndFlush(lowRatedContent);
    contentRepository.saveAndFlush(highRatedContent);
    entityManager.clear();

    CursorResponse<Content> result = contentRepository.findContentsByCursor(
        null,
        null,
        null,
        null,
        null,
        10,
        "rate",
        SortDirection.DESCENDING
    );

    assertThat(result.data())
        .extracting(Content::getAverageRating)
        .containsExactly(4.5, 2.0);
    assertThat(result.sortBy()).isEqualTo("rate");
  }

  @Test
  @DisplayName("임시 watcherCount 정렬은 0 커서와 ID 보조 정렬로 동작한다")
  void findContentsByTemporaryWatcherCountSort() {
    Content firstContent = contentRepository.saveAndFlush(Content.createManual(
        ContentType.MOVIE,
        "첫 번째 영화",
        "watcherCount 임시 정렬 확인용 영화",
        null,
        List.of("영화")
    ));
    Content secondContent = contentRepository.saveAndFlush(Content.createManual(
        ContentType.MOVIE,
        "두 번째 영화",
        "watcherCount 임시 정렬 확인용 영화",
        null,
        List.of("영화")
    ));
    entityManager.clear();

    CursorResponse<Content> result = contentRepository.findContentsByCursor(
        null,
        null,
        null,
        null,
        null,
        1,
        "watcherCount",
        SortDirection.ASCENDING
    );

    assertThat(result.data()).hasSize(1);
    assertThat(result.nextCursor()).isEqualTo("0");
    assertThat(result.nextIdAfter()).isNotNull();
    assertThat(result.sortBy()).isEqualTo("watcherCount");
  }
}
