package io.mopl.infra.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mopl.notification.kafka")
public record NotificationKafkaProperties(
    String topic,
    String dltTopic,
    String groupId,
    long maxRetries,
    Duration retryBackoff
) {
}
