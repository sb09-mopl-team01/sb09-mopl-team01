package io.mopl.infra.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "mopl.notification", name = "delivery-mode", havingValue = "kafka")
@EnableConfigurationProperties(NotificationKafkaProperties.class)
public class NotificationKafkaConfig {

  @Bean
  public DefaultErrorHandler notificationKafkaErrorHandler(
      KafkaTemplate<?, ?> kafkaTemplate,
      NotificationKafkaProperties properties
  ) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
        kafkaTemplate,
        (record, exception) -> new TopicPartition(properties.dltTopic(), record.partition())
    );
    return new DefaultErrorHandler(recoverer,
        new FixedBackOff(properties.retryBackoff().toMillis(), properties.maxRetries()));
  }
}
