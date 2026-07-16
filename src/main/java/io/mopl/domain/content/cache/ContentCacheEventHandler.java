package io.mopl.domain.content.cache;

import io.mopl.domain.content.event.ReviewStatsChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ContentCacheEventHandler {

  private final ContentCacheService contentCacheService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleReviewStatsChanged(ReviewStatsChangedEvent event) {
    contentCacheService.evictStats(event.contentId());
  }
}
