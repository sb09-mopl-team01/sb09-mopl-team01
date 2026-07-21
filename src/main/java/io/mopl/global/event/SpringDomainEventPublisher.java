package io.mopl.global.event;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpringDomainEventPublisher implements DomainEventPublisher {

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void publish(Object event) {
    Object domainEvent = Objects.requireNonNull(event, "event must not be null");

    eventPublisher.publishEvent(domainEvent);
    log.debug("Spring domain event dispatched. eventType={}", domainEvent.getClass().getSimpleName());
  }
}
