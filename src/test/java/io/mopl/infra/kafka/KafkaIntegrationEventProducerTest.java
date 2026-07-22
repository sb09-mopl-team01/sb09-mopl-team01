package io.mopl.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import io.mopl.global.event.IntegrationEvent;
import io.mopl.infra.outbox.OutboxEvent;
import io.mopl.infra.outbox.OutboxProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class KafkaIntegrationEventProducerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @Test
  @DisplayName("등록한 integration event 스키마를 가진 JSON Schema envelope로 발행한다")
  void publishesJsonSchemaEnvelope() throws Exception {
    given(kafkaTemplate.send(any(ProducerRecord.class)))
        .willReturn(CompletableFuture.completedFuture(null));
    IntegrationEventJsonSchema schema = new IntegrationEventJsonSchema(OBJECT_MAPPER);
    KafkaIntegrationEventProducer producer = new KafkaIntegrationEventProducer(
        kafkaTemplate,
        OBJECT_MAPPER,
        outboxProperties(),
        schema
    );
    OutboxEvent event = outboxEvent();

    producer.publish(event);

    ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(recordCaptor.capture());
    Object value = recordCaptor.getValue().value();

    assertThat(value).isInstanceOf(JsonNode.class);
    assertThat(JsonSchemaUtils.isEnvelope(value)).isTrue();
    assertThat(JsonSchemaUtils.getSchema(value).canonicalString())
        .isEqualTo(schema.getSchema().canonicalString());
    assertThat(JsonSchemaUtils.getValue(value)).isInstanceOf(JsonNode.class);
  }

  @Test
  @DisplayName("자동 등록 없이 등록된 notification-value 스키마를 조회해 직렬화한다")
  void serializesWithRegisteredSchemaWhenAutoRegistrationIsDisabled() throws Exception {
    IntegrationEventJsonSchema schema = new IntegrationEventJsonSchema(OBJECT_MAPPER);
    MockSchemaRegistryClient schemaRegistry = new MockSchemaRegistryClient();
    schemaRegistry.register("notification-value", schema.getSchema());
    KafkaJsonSchemaSerializer<Object> serializer = new KafkaJsonSchemaSerializer<>(schemaRegistry);
    serializer.configure(Map.of(
        AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://notification",
        AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false
    ), false);
    JsonNode payload = OBJECT_MAPPER.createObjectNode().put("eventType", "NotificationRequestedEvent");

    byte[] serialized = serializer.serialize(
        "notification",
        JsonSchemaUtils.envelope(schema.getSchema(), payload)
    );

    assertThat(serialized).isNotEmpty();
  }

  private OutboxEvent outboxEvent() {
    IntegrationEvent integrationEvent = new IntegrationEvent(
        "notification",
        UUID.randomUUID().toString(),
        "NotificationRequestedEvent",
        1,
        "Notification",
        UUID.randomUUID(),
        OBJECT_MAPPER.createObjectNode().put("title", "새 알림")
    );
    return OutboxEvent.create(integrationEvent, "{\"title\":\"새 알림\"}", Instant.now());
  }

  private OutboxProperties outboxProperties() {
    return new OutboxProperties(
        true,
        Duration.ofSeconds(1),
        10,
        Duration.ofMinutes(1),
        5,
        Duration.ofSeconds(1),
        2.0,
        Duration.ofMinutes(1),
        Duration.ofDays(1),
        Duration.ofHours(1),
        Duration.ofSeconds(5)
    );
  }
}
