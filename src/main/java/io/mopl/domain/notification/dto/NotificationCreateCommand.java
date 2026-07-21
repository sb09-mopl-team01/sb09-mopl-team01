package io.mopl.domain.notification.dto;

import io.mopl.domain.notification.entity.NotificationLevel;
import java.util.UUID;

public record NotificationCreateCommand(
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {
}
