package io.mopl.domain.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.global.event.IntegrationEvent;
import io.mopl.global.event.IntegrationEventPublisher;
import io.mopl.infra.kafka.NotificationKafkaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mopl.notification", name = "delivery-mode", havingValue = "kafka")
public class KafkaNotificationRequestedEventHandler {

  private static final String AGGREGATE_TYPE = "notification";

  private final IntegrationEventPublisher integrationEventPublisher;
  private final ObjectMapper objectMapper;
  private final NotificationKafkaProperties properties;

  @Transactional(propagation = Propagation.REQUIRED)
  public void handle(NotificationRequestedEvent event) {
    integrationEventPublisher.publish(new IntegrationEvent(
        properties.topic(),
        deduplicationKey(event),
        NotificationRequestedEvent.class.getSimpleName(),
        1,
        AGGREGATE_TYPE,
        event.sourceEventId(),
        objectMapper.valueToTree(event)
    ));
  }

  private String deduplicationKey(NotificationRequestedEvent event) {
    return event.sourceEventId() + ":" + event.receiverId();
  }
}
