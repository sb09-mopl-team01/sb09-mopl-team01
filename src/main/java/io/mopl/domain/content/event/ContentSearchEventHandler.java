package io.mopl.domain.content.event;

import io.mopl.domain.content.service.ContentSearchIndexService;
import io.mopl.domain.watchingsession.event.WatchingSessionEnteredEvent;
import io.mopl.domain.watchingsession.event.WatchingSessionLeftEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ContentSearchEventHandler {

  private final ContentSearchIndexService contentSearchIndexService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleReviewStatsChanged(ReviewStatsChangedEvent event) {
    contentSearchIndexService.index(event.contentId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleWatchingSessionEntered(WatchingSessionEnteredEvent event) {
    contentSearchIndexService.synchronizeWatcherCount(event.watchingSession().content().id());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleWatchingSessionLeft(WatchingSessionLeftEvent event) {
    contentSearchIndexService.synchronizeWatcherCount(event.watchingSession().content().id());
  }
}
