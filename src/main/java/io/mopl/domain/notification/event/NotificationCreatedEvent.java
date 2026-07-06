package io.mopl.domain.notification.event;

import io.mopl.domain.notification.dto.NotificationDto;
import java.time.Instant;
import java.util.UUID;

public record NotificationCreatedEvent(
    UUID notificationId,
    UUID receiverId,
    NotificationDto notification,
    Instant occurredAt
) {
}
