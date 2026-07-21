package io.mopl.domain.notification.realtime;

import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.event.NotificationCreatedEvent;
import java.time.Instant;
import java.util.UUID;

final class NotificationRealtimeFixtures {

  private NotificationRealtimeFixtures() {
  }

  static NotificationCreatedEvent event() {
    UUID notificationId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    return new NotificationCreatedEvent(
        notificationId,
        receiverId,
        new NotificationDto(
            notificationId,
            Instant.parse("2026-07-16T10:00:00Z"),
            receiverId,
            "새 알림",
            "알림 내용",
            NotificationLevel.INFO,
            false
        ),
        Instant.parse("2026-07-16T10:00:01Z")
    );
  }
}
