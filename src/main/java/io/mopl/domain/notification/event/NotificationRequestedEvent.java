package io.mopl.domain.notification.event;

import io.mopl.domain.notification.entity.NotificationLevel;
import java.util.UUID;

/**
 * 알림 생성에 필요한 정보를 전달하는 내부 이벤트입니다.
 */
public record NotificationRequestedEvent(
    UUID sourceEventId,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {
}
