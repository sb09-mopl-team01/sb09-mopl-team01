package io.mopl.domain.notification.event;

import io.mopl.global.sse.SseNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationSseEventHandler {

  private final SseNotificationService sseNotificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleCreated(NotificationCreatedEvent event) {
    sseNotificationService.sendNotification(
        event.receiverId(),
        event.notificationId(),
        event.notification()
    );
  }
}
