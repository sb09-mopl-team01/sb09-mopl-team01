package io.mopl.domain.notification.event;

import java.time.Instant;
import java.util.UUID;

/**
 * DB 읽음 처리 이후 Redis 캐시 갱신이나 Kafka 발행으로 확장하기 위한 애플리케이션 이벤트입니다.
 */
public record NotificationReadEvent(
    UUID notificationId,
    UUID receiverId,
    Instant occurredAt
) {
}
