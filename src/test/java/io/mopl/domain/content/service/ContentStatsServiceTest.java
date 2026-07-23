package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.mopl.domain.content.dto.ContentStats;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
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
