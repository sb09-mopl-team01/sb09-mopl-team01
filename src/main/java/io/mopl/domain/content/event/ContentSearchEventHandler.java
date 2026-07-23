package io.mopl.domain.content.event;

import io.mopl.domain.content.service.ContentSearchIndexService;
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
}
