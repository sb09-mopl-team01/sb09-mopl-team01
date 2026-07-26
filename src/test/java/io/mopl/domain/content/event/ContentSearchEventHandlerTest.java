package io.mopl.domain.content.event;

import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.content.service.ContentSearchIndexService;
import io.mopl.domain.watchingsession.dto.WatchingSessionDto;
import io.mopl.domain.watchingsession.event.WatchingSessionEnteredEvent;
import io.mopl.domain.watchingsession.event.WatchingSessionLeftEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentSearchEventHandlerTest {

  @Mock
  private ContentSearchIndexService contentSearchIndexService;

  @InjectMocks
  private ContentSearchEventHandler contentSearchEventHandler;

  @Test
  void reindexesContentWhenReviewStatsChange() {
    UUID contentId = UUID.randomUUID();

    contentSearchEventHandler.handleReviewStatsChanged(new ReviewStatsChangedEvent(contentId));

    verify(contentSearchIndexService).index(contentId);
  }

  @Test
  void synchronizesWatcherCountWhenWatchingSessionEnters() {
    UUID contentId = UUID.randomUUID();
    WatchingSessionDto watchingSession = watchingSession(contentId);

    contentSearchEventHandler.handleWatchingSessionEntered(
        new WatchingSessionEnteredEvent(watchingSession, 3L, Instant.now())
    );

    verify(contentSearchIndexService).synchronizeWatcherCount(contentId);
  }

  @Test
  void synchronizesWatcherCountWhenWatchingSessionLeaves() {
    UUID contentId = UUID.randomUUID();
    WatchingSessionDto watchingSession = watchingSession(contentId);

    contentSearchEventHandler.handleWatchingSessionLeft(
        new WatchingSessionLeftEvent(watchingSession, 0L, Instant.now())
    );

    verify(contentSearchIndexService).synchronizeWatcherCount(contentId);
  }

  private WatchingSessionDto watchingSession(UUID contentId) {
    return new WatchingSessionDto(
        UUID.randomUUID(),
        Instant.now(),
        null,
        ContentSummary.builder().id(contentId).build()
    );
  }
}
