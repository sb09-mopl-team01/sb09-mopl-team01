package io.mopl.domain.notification.event;

import io.mopl.domain.notification.dto.NotificationCreateCommand;
import java.util.UUID;

/**
 * 전달 방식과 무관하게 알림 생성 서비스에 전달하는 메시지입니다.
 */
public record NotificationMessage(
    UUID eventId,
    NotificationCreateCommand command
) {
}
