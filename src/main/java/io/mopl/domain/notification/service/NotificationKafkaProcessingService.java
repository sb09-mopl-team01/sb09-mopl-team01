package io.mopl.domain.notification.service;

import io.mopl.domain.notification.event.NotificationMessage;
import io.mopl.domain.notification.repository.ProcessedKafkaEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaProcessingService {

  private final ProcessedKafkaEventRepository processedKafkaEventRepository;
  private final NotificationService notificationService;

  @Transactional
  public void process(NotificationMessage message) {
    String eventKey = message.eventId().toString();
    if (!processedKafkaEventRepository.registerIfAbsent(eventKey, Instant.now())) {
      log.info("Duplicate notification Kafka event ignored. eventId={}", eventKey);
      return;
    }
    notificationService.create(message.command());
  }
}
