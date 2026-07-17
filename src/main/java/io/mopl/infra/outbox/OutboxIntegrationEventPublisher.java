package io.mopl.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.global.event.IntegrationEvent;
import io.mopl.global.event.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxIntegrationEventPublisher implements IntegrationEventPublisher {

  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void publish(IntegrationEvent event) {
    if (outboxEventRepository.findByDeduplicationKey(event.key()).isPresent()) {
      log.debug("Duplicate integration event ignored. deduplicationKey={}", event.key());
      return;
    }
    String payload = serialize(event);
    OutboxEvent outboxEvent = OutboxEvent.create(event, payload, Instant.now(clock));
    outboxEventRepository.save(outboxEvent);
    log.debug("Integration event saved to outbox. eventId={}, eventType={}",
        outboxEvent.getId(), outboxEvent.getEventType());
  }

  private String serialize(IntegrationEvent event) {
    try {
      return objectMapper.writeValueAsString(event.payload());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Integration event payload serialization failed.", e);
    }
  }
}
