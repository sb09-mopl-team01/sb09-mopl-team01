package io.mopl.infra.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "mopl.notification", name = "delivery-mode", havingValue = "kafka")
@EnableConfigurationProperties(NotificationKafkaProperties.class)
public class NotificationKafkaConfig {

  @Bean
  public DefaultErrorHandler notificationKafkaErrorHandler(
      KafkaTemplate<?, ?> kafkaTemplate,
      KafkaProperties kafkaProperties,
      NotificationKafkaProperties properties
  ) {
    Map<Class<?>, KafkaOperations<? extends Object, ? extends Object>> dltTemplates = new LinkedHashMap<>();
    dltTemplates.put(byte[].class, notificationDltBytesKafkaTemplate(kafkaProperties));
    dltTemplates.put(Object.class, kafkaTemplate);
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
        dltTemplates,
        (record, exception) -> new TopicPartition(properties.dltTopic(), record.partition())
    );
    recoverer.setFailIfSendResultIsError(true);
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
        new FixedBackOff(properties.retryBackoff().toMillis(), properties.maxRetries()));
    errorHandler.addNotRetryableExceptions(IllegalArgumentException.class,
        DeserializationException.class);
    return errorHandler;
  }

  /**
   * ErrorHandlingDeserializer가 보관한 실패 원본 byte[]를 DLT에 그대로 발행합니다.
   * 일반 JSON Schema 객체는 기본 KafkaTemplate의 KafkaJsonSchemaSerializer를 사용합니다.
   */
  private KafkaTemplate<String, byte[]> notificationDltBytesKafkaTemplate(KafkaProperties kafkaProperties) {
    Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties(null);
    producerProperties.put("value.serializer", ByteArraySerializer.class);
    ProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(producerProperties);
    return new KafkaTemplate<>(producerFactory);
  }
}
