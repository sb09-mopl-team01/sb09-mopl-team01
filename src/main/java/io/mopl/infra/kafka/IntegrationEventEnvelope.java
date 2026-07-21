package io.mopl.infra.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import io.mopl.infra.outbox.OutboxEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka consumer에게 전달되는 JSON Schema 계약의 런타임 표현입니다.
 */
public record IntegrationEventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    String aggregateType,
    UUID aggregateId,
    JsonNode payload
) {

  public static IntegrationEventEnvelope from(OutboxEvent event, JsonNode payload) {
    return new IntegrationEventEnvelope(
        event.getId(),
        event.getEventType(),
        event.getEventVersion(),
        event.getCreatedAt(),
        event.getAggregateType(),
        event.getAggregateId(),
        payload
    );
  }
}
