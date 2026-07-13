package io.mopl.infra.outbox;

import io.mopl.infra.kafka.KafkaIntegrationEventProducer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = {"mopl.kafka.enabled", "mopl.outbox.relay-enabled"}, havingValue = "true")
public class OutboxRelay {

  private final OutboxEventClaimService outboxEventClaimService;
  private final OutboxEventStateService outboxEventStateService;
  private final KafkaIntegrationEventProducer kafkaIntegrationEventProducer;

  @Scheduled(fixedDelayString = "${mopl.outbox.relay-delay:PT1S}")
  public void relay() {
    outboxEventStateService.recoverExpiredClaims();

    List<OutboxEvent> events = outboxEventClaimService.claimAvailableEvents();
    for (OutboxEvent event : events) {
      publish(event);
    }
  }

  private void publish(OutboxEvent event) {
    try {
      kafkaIntegrationEventProducer.publish(event);
      outboxEventStateService.markPublished(event.getId());
      log.debug("Outbox event published. eventId={}, eventType={}",
          event.getId(), event.getEventType());
    } catch (RuntimeException e) {
      outboxEventStateService.markPublishFailed(event.getId(), e);
      log.warn("Outbox event publish failed. eventId={}, eventType={}",
          event.getId(), event.getEventType(), e);
    }
  }
}
