package io.mopl.domain.notification.event;

import io.mopl.domain.notification.realtime.NotificationRealtimePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationSseEventHandler {

  private final NotificationRealtimePublisher notificationRealtimePublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleCreated(NotificationCreatedEvent event) {
    notificationRealtimePublisher.publish(event);
  }
}
