package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.mopl.domain.content.dto.ContentStats;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentStatsServiceTest {

  @Mock
  private WatchingSessionRepository watchingSessionRepository;

  @Test
  void getStatsIncludesWatcherCount() {
    Content content = content("로비 노출 콘텐츠");
    UUID contentId = UUID.randomUUID();
    ReflectionTestUtils.setField(content, "id", contentId);
    content.updateReviewStats(4.2, 3);
    ContentStatsService contentStatsService = new ContentStatsService(watchingSessionRepository);
    given(watchingSessionRepository.countByContentId(contentId, null)).willReturn(5L);

    ContentStats result = contentStatsService.getStats(content);

    assertThat(result.averageRating()).isEqualTo(4.2);
    assertThat(result.reviewCount()).isEqualTo(3);
    assertThat(result.watcherCount()).isEqualTo(5L);
  }

  @Test
  void getStatsByContentsLoadsWatcherCountsAtOnce() {
    Content firstContent = content("첫 번째 콘텐츠");
    Content secondContent = content("두 번째 콘텐츠");
    UUID firstContentId = UUID.randomUUID();
    UUID secondContentId = UUID.randomUUID();
    ReflectionTestUtils.setField(firstContent, "id", firstContentId);
    ReflectionTestUtils.setField(secondContent, "id", secondContentId);
    firstContent.updateReviewStats(4.5, 10);
    secondContent.updateReviewStats(3.5, 2);
    ContentStatsService contentStatsService = new ContentStatsService(watchingSessionRepository);
    given(watchingSessionRepository.countByContentIds(List.of(firstContentId, secondContentId)))
        .willReturn(Map.of(firstContentId, 2L));

    Map<UUID, ContentStats> result = contentStatsService.getStatsByContents(
        List.of(firstContent, secondContent)
    );

    assertThat(result.get(firstContentId).watcherCount()).isEqualTo(2L);
    assertThat(result.get(secondContentId).watcherCount()).isZero();
    verify(watchingSessionRepository).countByContentIds(List.of(firstContentId, secondContentId));
  }

  @Test
  void getStatsByEmptyContentsDoesNotQueryWatcherCount() {
    ContentStatsService contentStatsService = new ContentStatsService(watchingSessionRepository);

    Map<UUID, ContentStats> result = contentStatsService.getStatsByContents(List.of());

    assertThat(result).isEmpty();
    verifyNoInteractions(watchingSessionRepository);
  }

  private Content content(String title) {
    return Content.createManual(
        ContentType.MOVIE,
        title,
        title + " 설명",
        null,
        Set.of("영화")
    );
  }
}
