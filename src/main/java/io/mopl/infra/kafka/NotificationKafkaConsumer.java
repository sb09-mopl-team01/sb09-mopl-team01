package io.mopl.infra.kafka;

import io.mopl.domain.notification.event.NotificationMessageFactory;
import io.mopl.domain.notification.service.NotificationKafkaProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "mopl.notification", name = "delivery-mode", havingValue = "kafka")
@ConditionalOnProperty(prefix = "mopl.kafka", name = "enabled", havingValue = "true")
public class NotificationKafkaConsumer {

  private final NotificationMessageFactory notificationMessageFactory;
  private final NotificationKafkaProcessingService notificationKafkaProcessingService;

  @KafkaListener(
      topics = "${mopl.notification.kafka.topic}",
      groupId = "${mopl.notification.kafka.group-id}"
  )
  public void consume(ConsumerRecord<String, Object> record) {
    try {
      notificationKafkaProcessingService.process(notificationMessageFactory.fromKafkaRecord(record.value()));
    } catch (RuntimeException e) {
      log.error("Notification Kafka event processing failed. valueType={}",
          record.value() == null ? null : record.value().getClass().getName(), e);
      throw e;
    }
  }
}
