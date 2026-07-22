package io.mopl.infra.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils;
import io.mopl.infra.outbox.OutboxEvent;
import io.mopl.infra.outbox.OutboxProperties;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mopl.kafka.enabled", havingValue = "true")
public class KafkaIntegrationEventProducer {

  private static final String EVENT_ID_HEADER = "mopl-event-id";
  private static final String EVENT_TYPE_HEADER = "mopl-event-type";
  private static final String EVENT_VERSION_HEADER = "mopl-event-version";

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final OutboxProperties outboxProperties;
  private final IntegrationEventJsonSchema integrationEventJsonSchema;

  /**
   * 브로커 ACK를 확인한 뒤에만 Relay가 Outbox 이벤트를 PUBLISHED로 변경할 수 있도록 대기합니다.
   */
  public void publish(OutboxEvent event) {
    ProducerRecord<String, Object> record = new ProducerRecord<>(
        event.getTopic(),
        event.getEventKey(),
        JsonSchemaUtils.envelope(
            integrationEventJsonSchema.getSchema(),
            objectMapper.valueToTree(IntegrationEventEnvelope.from(event, readPayload(event)))
        )
    );
    addHeaders(record, event);

    try {
      kafkaTemplate.send(record).get(outboxProperties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Kafka publish interrupted. eventId=" + event.getId(), e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Kafka publish failed. eventId=" + event.getId(), e);
    }
  }

  private JsonNode readPayload(OutboxEvent event) {
    try {
      return objectMapper.readTree(event.getPayload());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Outbox payload is not valid JSON. eventId=" + event.getId(), e);
    }
  }

  private void addHeaders(ProducerRecord<String, Object> record, OutboxEvent event) {
    record.headers().add(EVENT_ID_HEADER, event.getId().toString().getBytes(StandardCharsets.UTF_8));
    record.headers().add(EVENT_TYPE_HEADER, event.getEventType().getBytes(StandardCharsets.UTF_8));
    record.headers().add(EVENT_VERSION_HEADER,
        Integer.toString(event.getEventVersion()).getBytes(StandardCharsets.UTF_8));
  }
}
