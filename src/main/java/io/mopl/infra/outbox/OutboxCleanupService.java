package io.mopl.infra.outbox;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mopl.outbox.relay-enabled", havingValue = "true")
public class OutboxCleanupService {

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxProperties outboxProperties;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${mopl.outbox.cleanup-delay:PT1H}")
  @Transactional
  public void deleteExpiredPublishedEvents() {
    Instant publishedBefore = Instant.now(clock).minus(outboxProperties.publishedRetention());
    int deleted = outboxEventRepository.deletePublishedBefore(publishedBefore);
    if (deleted > 0) {
      log.info("Deleted expired published outbox events. count={}", deleted);
    }
  }
}
