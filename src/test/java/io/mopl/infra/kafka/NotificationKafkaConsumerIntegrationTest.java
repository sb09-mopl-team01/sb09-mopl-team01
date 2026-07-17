package io.mopl.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.repository.NotificationRepository;
import io.mopl.domain.notification.event.NotificationRequestedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("kafka-integration")
@EmbeddedKafka(partitions = 1, topics = {"notification", "notification-dlt"})
class NotificationKafkaConsumerIntegrationTest {

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private EmbeddedKafkaBroker embeddedKafkaBroker;

  private Consumer<String, byte[]> dltConsumer;

  @AfterEach
  void closeConsumer() {
    if (dltConsumer != null) {
      dltConsumer.close();
    }
  }

  @Test
  @DisplayName("같은 Kafka eventId를 다시 받아도 알림은 한 번만 저장한다")
  void consumesDuplicateEventOnlyOnce() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    String payload = envelope(eventId, receiverId, "중복 방지", "한 번만 저장됩니다");

    kafkaTemplate.send("notification", receiverId.toString(), payload).get();
    kafkaTemplate.send("notification", receiverId.toString(), payload).get();

    await(() -> notificationRepository.countByReceiverId(receiverId) == 1);
    assertThat(notificationRepository.countByReceiverId(receiverId)).isOne();
  }

  @Test
  @DisplayName("처리할 수 없는 Kafka 이벤트는 재시도 후 notification-dlt로 이동한다")
  void sendsInvalidEventToDlt() throws Exception {
    dltConsumer = dltConsumer();
    kafkaTemplate.send("notification", "invalid", "{\"eventType\":\"invalid\"}").get();

    ConsumerRecord<String, byte[]> record = awaitDltRecord(
        candidate -> new String(candidate.value()).contains("invalid"));

    assertThat(record.topic()).isEqualTo("notification-dlt");
  }

  @Test
  @DisplayName("poll 단계 바이너리 역직렬화 실패는 원본 byte[]를 보존해 DLT로 이동한다")
  void sendsDeserializationFailureToDltWithOriginalBytes() throws Exception {
    byte[] invalidPayload = new byte[]{0, 1, 2, 3};
    dltConsumer = dltConsumer();

    try (KafkaProducer<String, byte[]> producer = rawBytesProducer()) {
      producer.send(new ProducerRecord<>("notification", "invalid-binary", invalidPayload)).get();
    }

    ConsumerRecord<String, byte[]> record = awaitDltRecord(
        candidate -> "invalid-binary".equals(candidate.key()));

    assertThat(record.topic()).isEqualTo("notification-dlt");
    assertThat(record.value()).isEqualTo(invalidPayload);
  }

  private String envelope(UUID eventId, UUID receiverId, String title, String content) throws Exception {
    NotificationRequestedEvent requestedEvent = new NotificationRequestedEvent(
        UUID.randomUUID(), receiverId, title, content, NotificationLevel.INFO);
    return objectMapper.writeValueAsString(Map.of(
        "eventId", eventId,
        "eventType", NotificationRequestedEvent.class.getSimpleName(),
        "eventVersion", 1,
        "occurredAt", Instant.now(),
        "aggregateType", "notification",
        "aggregateId", requestedEvent.sourceEventId(),
        "payload", requestedEvent
    ));
  }

  private Consumer<String, byte[]> dltConsumer() {
    Map<String, Object> properties = KafkaTestUtils.consumerProps(
        "notification-dlt-verifier-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
    Consumer<String, byte[]> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(properties);
    consumer.subscribe(Collections.singleton("notification-dlt"));
    return consumer;
  }

  private KafkaProducer<String, byte[]> rawBytesProducer() {
    Map<String, Object> properties = KafkaTestUtils.producerProps(embeddedKafkaBroker);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    return new KafkaProducer<>(properties);
  }

  private ConsumerRecord<String, byte[]> awaitDltRecord(Predicate<ConsumerRecord<String, byte[]>> condition) {
    Instant timeout = Instant.now().plusSeconds(10);
    while (Instant.now().isBefore(timeout)) {
      ConsumerRecords<String, byte[]> records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(1));
      for (ConsumerRecord<String, byte[]> record : records) {
        if (condition.test(record)) {
          return record;
        }
      }
    }
    throw new AssertionError("조건에 맞는 DLT 레코드를 찾지 못했습니다.");
  }

  private void await(Condition condition) throws InterruptedException {
    Instant timeout = Instant.now().plusSeconds(10);
    while (Instant.now().isBefore(timeout)) {
      if (condition.matches()) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Kafka consumer did not process the record within 10 seconds");
  }

  @FunctionalInterface
  private interface Condition {

    boolean matches();
  }
}
