package io.mopl.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.watchingsession.entity.WatchingSession;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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
  @DisplayName("외부 콘텐츠를 source, type, externalId 조합으로 조회한다")
  void findExternalContentBySourceTypeAndExternalId() {
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

    assertThat(contentRepository.existsBySourceAndTypeAndExternalId(
        ContentSource.TMDB,
        ContentType.TV_SERIES,
        "12345"
    ))
        .isTrue();
    assertThat(contentRepository.findBySourceAndTypeAndExternalId(
        ContentSource.TMDB,
        ContentType.TV_SERIES,
        "12345"
    ))
        .isPresent()
        .get()
        .extracting(Content::getLastSyncedAt)
        .isEqualTo(syncedAt);
  }

  @Test
  @DisplayName("TMDB 영화와 TV는 같은 externalId를 각각 저장할 수 있다")
  void saveTmdbMovieAndTvWithSameExternalId() {
    Instant syncedAt = Instant.parse("2026-06-25T00:00:00Z");
    Content movie = Content.createExternal(
        ContentType.MOVIE,
        "영화",
        "TMDB 영화",
        null,
        ContentSource.TMDB,
        "100",
        syncedAt,
        List.of("영화")
    );
    Content tvSeries = Content.createExternal(
        ContentType.TV_SERIES,
        "TV 시리즈",
        "TMDB TV 시리즈",
        null,
        ContentSource.TMDB,
        "100",
        syncedAt,
        List.of("TV")
    );

    contentRepository.saveAllAndFlush(List.of(movie, tvSeries));
    entityManager.clear();

    assertThat(contentRepository.findBySourceAndTypeAndExternalId(
        ContentSource.TMDB,
        ContentType.MOVIE,
        "100"
    )).isPresent();
    assertThat(contentRepository.findBySourceAndTypeAndExternalId(
        ContentSource.TMDB,
        ContentType.TV_SERIES,
        "100"
    )).isPresent();
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

    CursorResponse<UUID> result = contentRepository.findContentIdsByCursor(
        ContentType.MOVIE,
        "우주",
        List.of("SF"),
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(result.data()).containsExactly(movie.getId());
    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(result.hasNext()).isFalse();
  }

  @Test
  @DisplayName("1~2글자 키워드와 3글자 이상 키워드를 제목과 설명에서 검색한다")
  void findContentsByShortAndLongKeywords() {
    Content titleMatch = contentRepository.saveAndFlush(Content.createManual(
        ContentType.MOVIE,
        "우주 탐험 영화",
        "제목에서 짧은 키워드가 검색되는 콘텐츠",
        null,
        List.of("SF")
    ));
    Content descriptionMatch = contentRepository.saveAndFlush(Content.createManual(
        ContentType.TV_SERIES,
        "가족 이야기",
        "로맨스 장르와 우주 이야기를 함께 다루는 콘텐츠",
        null,
        List.of("로맨스")
    ));
    entityManager.clear();

    CursorResponse<UUID> shortKeywordResult = contentRepository.findContentIdsByCursor(
        null, "우주", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    );
    CursorResponse<UUID> longKeywordResult = contentRepository.findContentIdsByCursor(
        null, "로맨스", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    );
    CursorResponse<UUID> oneCharacterResult = contentRepository.findContentIdsByCursor(
        null, "우", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    );

    assertThat(shortKeywordResult.data())
        .containsExactlyInAnyOrder(titleMatch.getId(), descriptionMatch.getId());
    assertThat(shortKeywordResult.totalCount()).isEqualTo(2);
    assertThat(longKeywordResult.data()).containsExactly(descriptionMatch.getId());
    assertThat(longKeywordResult.totalCount()).isEqualTo(1);
    assertThat(oneCharacterResult.data())
        .containsExactlyInAnyOrder(titleMatch.getId(), descriptionMatch.getId());
  }

  @Test
  @DisplayName("짧은 특수문자 키워드를 와일드카드가 아닌 문자 그대로 검색한다")
  void findContentsByLiteralSpecialCharacterKeyword() {
    Content content = contentRepository.saveAndFlush(Content.createManual(
        ContentType.MOVIE,
        "100% 확실한 A_B! 영화",
        "특수문자 검색 검증 콘텐츠",
        null,
        List.of("테스트")
    ));
    entityManager.clear();

    for (String keyword : List.of("%", "_", "!")) {
      CursorResponse<UUID> result = contentRepository.findContentIdsByCursor(
          null, keyword, null, null, null, 10, "createdAt", SortDirection.DESCENDING
      );

      assertThat(result.data()).containsExactly(content.getId());
      assertThat(result.totalCount()).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("여러 태그가 동시에 일치해도 콘텐츠 totalCount는 중복되지 않는다")
  void countContentOnceWhenMultipleTagsMatch() {
    Content content = contentRepository.saveAndFlush(Content.createManual(
        ContentType.MOVIE,
        "태그 중복 카운트 검증 영화",
        "액션과 SF 태그가 모두 일치하는 콘텐츠",
        null,
        List.of("액션", "SF")
    ));
    entityManager.clear();

    CursorResponse<UUID> result = contentRepository.findContentIdsByCursor(
        null, null, List.of("액션", "SF"), null, null, 10,
        "createdAt", SortDirection.DESCENDING
    );

    assertThat(result.data()).containsExactly(content.getId());
    assertThat(result.totalCount()).isEqualTo(1);
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

    CursorResponse<UUID> result = contentRepository.findContentIdsByCursor(
        null,
        null,
        null,
        null,
        null,
        10,
        "rate",
        SortDirection.DESCENDING
    );

    assertThat(result.data()).containsExactly(highRatedContent.getId(), lowRatedContent.getId());
    assertThat(result.sortBy()).isEqualTo("rate");
  }

  @Test
  @DisplayName("생성일 커서는 이전 페이지의 마지막 행 다음부터 조회한다")
  void findContentsByCreatedAtCursorWithoutDuplicates() {
    List<Content> contents = List.of(
        contentRepository.saveAndFlush(Content.createManual(
            ContentType.MOVIE, "첫 번째", "생성일 커서 테스트", null, List.of("테스트"))),
        contentRepository.saveAndFlush(Content.createManual(
            ContentType.MOVIE, "두 번째", "생성일 커서 테스트", null, List.of("테스트"))),
        contentRepository.saveAndFlush(Content.createManual(
            ContentType.MOVIE, "세 번째", "생성일 커서 테스트", null, List.of("테스트"))),
        contentRepository.saveAndFlush(Content.createManual(
            ContentType.MOVIE, "네 번째", "생성일 커서 테스트", null, List.of("테스트")))
    );
    entityManager.clear();

    CursorResponse<UUID> firstPage = contentRepository.findContentIdsByCursor(
        null, null, null, null, null, 2, "createdAt", SortDirection.DESCENDING
    );
    CursorResponse<UUID> secondPage = contentRepository.findContentIdsByCursor(
        null, null, null, firstPage.nextCursor(), firstPage.nextIdAfter(),
        2, "createdAt", SortDirection.DESCENDING
    );

    assertThat(firstPage.data()).doesNotContainAnyElementsOf(secondPage.data());
    assertThat(firstPage.data()).hasSize(2);
    assertThat(secondPage.data()).hasSize(2);
    assertThat(java.util.stream.Stream.concat(firstPage.data().stream(), secondPage.data().stream()))
        .containsExactlyInAnyOrderElementsOf(contents.stream().map(Content::getId).toList());

    CursorResponse<UUID> ascendingFirstPage = contentRepository.findContentIdsByCursor(
        null, null, null, null, null, 2, "createdAt", SortDirection.ASCENDING
    );
    CursorResponse<UUID> ascendingSecondPage = contentRepository.findContentIdsByCursor(
        null, null, null, ascendingFirstPage.nextCursor(), ascendingFirstPage.nextIdAfter(),
        2, "createdAt", SortDirection.ASCENDING
    );
    assertThat(java.util.stream.Stream.concat(
        ascendingFirstPage.data().stream(), ascendingSecondPage.data().stream()))
        .containsExactlyInAnyOrderElementsOf(contents.stream().map(Content::getId).toList());
  }

  @Test
  @DisplayName("동일 평점 콘텐츠도 id 보조 커서로 중복 없이 조회한다")
  void findContentsByRateCursorWithoutDuplicates() {
    Content highest = Content.createManual(
        ContentType.MOVIE, "최고 평점", "평점 커서 테스트", null, List.of("테스트"));
    highest.updateReviewStats(5.0, 1);
    Content tiedFirst = Content.createManual(
        ContentType.MOVIE, "동점 하나", "평점 커서 테스트", null, List.of("테스트"));
    tiedFirst.updateReviewStats(4.0, 1);
    Content tiedSecond = Content.createManual(
        ContentType.MOVIE, "동점 둘", "평점 커서 테스트", null, List.of("테스트"));
    tiedSecond.updateReviewStats(4.0, 1);
    Content lowest = Content.createManual(
        ContentType.MOVIE, "최저 평점", "평점 커서 테스트", null, List.of("테스트"));
    lowest.updateReviewStats(3.0, 1);
    List<Content> contents = contentRepository.saveAllAndFlush(
        List.of(highest, tiedFirst, tiedSecond, lowest));
    entityManager.clear();

    CursorResponse<UUID> firstPage = contentRepository.findContentIdsByCursor(
        null, null, null, null, null, 2, "rate", SortDirection.DESCENDING
    );
    CursorResponse<UUID> secondPage = contentRepository.findContentIdsByCursor(
        null, null, null, firstPage.nextCursor(), firstPage.nextIdAfter(),
        2, "rate", SortDirection.DESCENDING
    );

    assertThat(firstPage.data()).doesNotContainAnyElementsOf(secondPage.data());
    assertThat(firstPage.data()).hasSize(2);
    assertThat(secondPage.data()).hasSize(2);
    assertThat(java.util.stream.Stream.concat(firstPage.data().stream(), secondPage.data().stream()))
        .containsExactlyInAnyOrderElementsOf(contents.stream().map(Content::getId).toList());

    CursorResponse<UUID> ascendingFirstPage = contentRepository.findContentIdsByCursor(
        null, null, null, null, null, 2, "rate", SortDirection.ASCENDING
    );
    CursorResponse<UUID> ascendingSecondPage = contentRepository.findContentIdsByCursor(
        null, null, null, ascendingFirstPage.nextCursor(), ascendingFirstPage.nextIdAfter(),
        2, "rate", SortDirection.ASCENDING
    );
    assertThat(java.util.stream.Stream.concat(
        ascendingFirstPage.data().stream(), ascendingSecondPage.data().stream()))
        .containsExactlyInAnyOrderElementsOf(contents.stream().map(Content::getId).toList());
  }

  @Test
  @DisplayName("인기순은 리뷰 수가 많고 같은 리뷰 수에서는 평점이 높은 순으로 정렬한다")
  void findContentsByWatcherCountReviewCountAndRatingSort() {
    Content mostReviewed = Content.createManual(
        ContentType.MOVIE,
        "리뷰 최다 영화",
        "리뷰 수 우선 정렬 확인용 영화",
        null,
        List.of("영화")
    );
    mostReviewed.updateReviewStats(2.0, 7);
    Content higherRated = Content.createManual(
        ContentType.MOVIE,
        "동률 고평점 영화",
        "평점 차순 정렬 확인용 영화",
        null,
        List.of("영화")
    );
    higherRated.updateReviewStats(4.8, 5);
    Content lowerRated = Content.createManual(
        ContentType.MOVIE,
        "동률 저평점 영화",
        "평점 차순 정렬 확인용 영화",
        null,
        List.of("영화")
    );
    lowerRated.updateReviewStats(3.0, 5);
    contentRepository.saveAllAndFlush(List.of(mostReviewed, higherRated, lowerRated));
    persistWatchingSessions(mostReviewed, 2);
    persistWatchingSessions(higherRated, 1);
    persistWatchingSessions(lowerRated, 1);
    entityManager.flush();
    entityManager.clear();

    CursorResponse<UUID> firstPage = contentRepository.findContentIdsByCursor(
        null,
        null,
        null,
        null,
        null,
        2,
        "watcherCount",
        SortDirection.DESCENDING
    );
    CursorResponse<UUID> secondPage = contentRepository.findContentIdsByCursor(
        null,
        null,
        null,
        firstPage.nextCursor(),
        firstPage.nextIdAfter(),
        2,
        "watcherCount",
        SortDirection.DESCENDING
    );

    assertThat(firstPage.data()).containsExactly(mostReviewed.getId(), higherRated.getId());
    assertThat(firstPage.nextCursor()).isEqualTo("1|5|4.8");
    assertThat(firstPage.sortBy()).isEqualTo("watcherCount");
    assertThat(secondPage.data()).containsExactly(lowerRated.getId());
  }

  private void persistWatchingSessions(Content content, int watcherCount) {
    for (int index = 0; index < watcherCount; index++) {
      User watcher = User.builder()
          .email(UUID.randomUUID() + "@example.com")
          .passwordHash("password")
          .name("watcher-" + UUID.randomUUID())
          .build();
      entityManager.persist(watcher);
      entityManager.persist(WatchingSession.start(watcher, content));
    }
  }

  @Test
  @DisplayName("활성 콘텐츠 ID를 UUID 키셋 기준으로 조회한다")
  void findActiveIdsAfterWithKeyset() {
    List<Content> contents = List.of(
        Content.createManual(ContentType.MOVIE, "영화 1", "설명", null, List.of("영화")),
        Content.createManual(ContentType.MOVIE, "영화 2", "설명", null, List.of("영화")),
        Content.createManual(ContentType.MOVIE, "영화 3", "설명", null, List.of("영화"))
    );
    contentRepository.saveAllAndFlush(contents);
    List<UUID> expectedIds = contents.stream().map(Content::getId).toList();

    List<UUID> firstPage = contentRepository.findActiveIdsAfter(
        null,
        PageRequest.of(0, 2)
    );
    List<UUID> secondPage = contentRepository.findActiveIdsAfter(
        firstPage.get(firstPage.size() - 1),
        PageRequest.of(0, 2)
    );

    assertThat(firstPage).hasSize(2);
    assertThat(secondPage).hasSize(1);
    assertThat(java.util.stream.Stream.concat(firstPage.stream(), secondPage.stream()))
        .containsExactlyInAnyOrderElementsOf(expectedIds);
  }

  @Test
  @DisplayName("삭제된 콘텐츠는 일반 조회와 목록에서 제외하고 외부 식별 조회에는 유지한다")
  void excludeSoftDeletedContentFromPublicQueries() {
    Content content = contentRepository.saveAndFlush(Content.createExternal(
        ContentType.MOVIE,
        "삭제된 영화",
        "소프트 삭제 조회 정책을 검증하는 영화",
        null,
        ContentSource.TMDB,
        "deleted-1",
        Instant.parse("2026-07-19T00:00:00Z"),
        List.of("영화")
    ));
    content.softDelete(Instant.parse("2026-07-20T00:00:00Z"));
    contentRepository.flush();
    entityManager.clear();

    CursorResponse<UUID> result = contentRepository.findContentIdsByCursor(
        null, null, null, null, null, 10, "createdAt", SortDirection.DESCENDING
    );

    assertThat(contentRepository.findById(content.getId())).isEmpty();
    assertThat(contentRepository.findAllByIdWithTags(List.of(content.getId()))).isEmpty();
    assertThat(result.data()).doesNotContain(content.getId());
    assertThat(contentRepository.findBySourceAndTypeAndExternalId(
        ContentSource.TMDB,
        ContentType.MOVIE,
        "deleted-1"
    )).isPresent().get().extracting(Content::isDeleted).isEqualTo(true);
  }

  @Test
  @DisplayName("보존 기간이 지난 삭제 콘텐츠 중 소유 썸네일이 있는 항목만 정리 대상으로 조회한다")
  void findThumbnailCleanupCandidates() {
    Instant cutoff = Instant.parse("2026-04-23T00:00:00Z");
    Content expired = contentRepository.save(Content.createManual(
        ContentType.MOVIE,
        "정리 대상",
        "보존 기간이 지난 콘텐츠",
        "/content-thumbnails/expired.jpg",
        "expired.jpg",
        List.of("영화")
    ));
    expired.softDelete(cutoff.minusSeconds(1));
    Content recent = contentRepository.save(Content.createManual(
        ContentType.MOVIE,
        "보존 대상",
        "보존 기간이 지나지 않은 콘텐츠",
        "/content-thumbnails/recent.jpg",
        "recent.jpg",
        List.of("영화")
    ));
    recent.softDelete(cutoff.plusSeconds(1));
    Content external = contentRepository.save(Content.createExternal(
        ContentType.MOVIE,
        "외부 이미지",
        "소유 썸네일 키가 없는 콘텐츠",
        "https://image.example.com/external.jpg",
        ContentSource.TMDB,
        "external-thumbnail",
        Instant.parse("2026-01-01T00:00:00Z"),
        List.of("영화")
    ));
    external.softDelete(cutoff.minusSeconds(1));
    contentRepository.flush();
    entityManager.clear();

    var result = contentRepository.findThumbnailCleanupCandidates(
        cutoff,
        PageRequest.of(0, 10)
    );

    assertThat(result.getContent()).singleElement().satisfies(candidate -> {
      assertThat(candidate.contentId()).isEqualTo(expired.getId());
      assertThat(candidate.thumbnailKey()).isEqualTo("expired.jpg");
    });
    assertThat(contentRepository.findByIdIncludingDeleted(expired.getId())).isPresent();
  }

}
