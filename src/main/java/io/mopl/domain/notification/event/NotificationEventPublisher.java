package io.mopl.domain.notification.event;

/**
 * 알림 이벤트 발행 포트입니다. 이후 구현체에서 Redis pub/sub, 캐시 갱신, Kafka 발행을 연결합니다.
 */
public interface NotificationEventPublisher {

  void publish(NotificationReadEvent event);
}
