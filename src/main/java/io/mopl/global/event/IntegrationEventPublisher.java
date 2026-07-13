package io.mopl.global.event;

/**
 * 도메인 트랜잭션과 함께 외부 전달용 이벤트를 영속화하는 포트입니다.
 */
public interface IntegrationEventPublisher {

  void publish(IntegrationEvent event);
}
