package io.mopl.domain.notification.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.notification.event.NotificationCreatedEvent;
import io.mopl.global.sse.SseNotificationService;
import org.junit.jupiter.api.Test;

class LocalNotificationPublisherTest {

  private final SseNotificationService sseNotificationService = mock(SseNotificationService.class);
  private final LocalNotificationPublisher publisher =
      new LocalNotificationPublisher(sseNotificationService);

  @Test
  void relaysNotificationToLocalSseConnections() {
    NotificationCreatedEvent event = NotificationRealtimeFixtures.event();

    publisher.publish(event);

    verify(sseNotificationService).sendNotification(
        event.receiverId(),
        event.notificationId(),
        event.notification()
    );
  }
}
