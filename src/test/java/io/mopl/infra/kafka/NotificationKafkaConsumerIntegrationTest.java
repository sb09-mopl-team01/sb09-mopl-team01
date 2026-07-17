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
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
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

  private Consumer<String, String> dltConsumer;

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

    ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(10));

    assertThat(records.count()).isGreaterThan(0);
    ConsumerRecord<String, String> record = records.iterator().next();
    assertThat(record.topic()).isEqualTo("notification-dlt");
    assertThat(record.value()).contains("invalid");
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

  private Consumer<String, String> dltConsumer() {
    Map<String, Object> properties = KafkaTestUtils.consumerProps(
        "notification-dlt-verifier-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(properties);
    consumer.subscribe(Collections.singleton("notification-dlt"));
    return consumer;
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
