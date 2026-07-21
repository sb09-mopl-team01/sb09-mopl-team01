package io.mopl.domain.content.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.event.ReviewStatsChangedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class ContentCacheEventHandlerTest {

  @Mock
  private ContentCacheService contentCacheService;

  @Test
  void reviewStatsEventEvictsOnlyStatsCache() {
    UUID contentId = UUID.randomUUID();
    ContentCacheEventHandler handler = new ContentCacheEventHandler(contentCacheService);

    handler.handleReviewStatsChanged(new ReviewStatsChangedEvent(contentId));

    verify(contentCacheService).evictStats(contentId);
  }

  @Test
  void reviewStatsEventIsHandledOnlyAfterCommit() throws Exception {
    TransactionalEventListener annotation = ContentCacheEventHandler.class
        .getMethod("handleReviewStatsChanged", ReviewStatsChangedEvent.class)
        .getAnnotation(TransactionalEventListener.class);

    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    assertThat(annotation.fallbackExecution()).isFalse();
  }
}
