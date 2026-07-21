package io.mopl.global.event;

/**
 * 여러 도메인에서 사용할 수 있는 도메인 이벤트 발행 포트입니다.
 */
public interface DomainEventPublisher {

  void publish(Object event);
}
