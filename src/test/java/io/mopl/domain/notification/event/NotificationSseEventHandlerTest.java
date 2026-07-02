package io.mopl.domain.notification.event;

import static org.mockito.Mockito.verify;

import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.global.sse.SseNotificationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationSseEventHandlerTest {

  private final SseNotificationService sseNotificationService = org.mockito.Mockito.mock(SseNotificationService.class);
  private final NotificationSseEventHandler eventHandler = new NotificationSseEventHandler(sseNotificationService);

  @Test
  @DisplayName("알림 생성 이벤트를 수신자 SSE 이벤트로 전송한다")
  void handleCreated() {
    UUID notificationId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    NotificationDto notification = new NotificationDto(
        notificationId,
        Instant.parse("2026-07-02T10:16:00Z"),
        receiverId,
        "새 알림",
        "알림 내용",
        NotificationLevel.INFO,
        false
    );
    NotificationCreatedEvent event = new NotificationCreatedEvent(
        notificationId,
        receiverId,
        notification,
        Instant.parse("2026-07-02T10:16:01Z")
    );

    eventHandler.handleCreated(event);

    verify(sseNotificationService).sendNotification(receiverId, notificationId, notification);
  }
}
