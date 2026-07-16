package io.mopl.domain.notification.realtime;

import io.mopl.domain.notification.event.NotificationCreatedEvent;
import io.mopl.global.sse.SseNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "mopl.notification.realtime.redis",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class LocalNotificationPublisher implements NotificationRealtimePublisher {

  private final SseNotificationService sseNotificationService;

  @Override
  public void publish(NotificationCreatedEvent event) {
    sseNotificationService.sendNotification(
        event.receiverId(),
        event.notificationId(),
        event.notification()
    );
  }
}
