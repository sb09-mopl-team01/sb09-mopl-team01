package io.mopl.infra.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventStateService {

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxProperties outboxProperties;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markPublished(UUID eventId) {
    outboxEventRepository.findById(eventId)
        .filter(event -> event.getStatus() == OutboxStatus.CLAIMED)
        .ifPresentOrElse(
            event -> event.markPublished(Instant.now(clock)),
            () -> log.warn("Ignoring publish completion for non-claimed outbox event. eventId={}", eventId)
        );
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markPublishFailed(UUID eventId, Throwable exception) {
    outboxEventRepository.findById(eventId)
        .filter(event -> event.getStatus() == OutboxStatus.CLAIMED)
        .ifPresentOrElse(event -> {
          Instant now = Instant.now(clock);
          event.markPublishFailed(
              now.plus(calculateBackoff(event.getRetryCount())),
              outboxProperties.maxAttempts(),
              exception.getMessage(),
              now
          );
        }, () -> log.warn("Ignoring publish failure for non-claimed outbox event. eventId={}", eventId));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int recoverExpiredClaims() {
    Instant now = Instant.now(clock);
    int recovered = outboxEventRepository.recoverExpiredClaims(
        now.minus(outboxProperties.claimTimeout()),
        now
    );
    if (recovered > 0) {
      log.warn("Recovered expired outbox claims. count={}", recovered);
    }
    return recovered;
  }

  private Duration calculateBackoff(int completedRetries) {
    double delayMillis = outboxProperties.initialBackoff().toMillis()
        * Math.pow(outboxProperties.backoffMultiplier(), completedRetries);
    long boundedDelayMillis = (long) Math.min(delayMillis, outboxProperties.maxBackoff().toMillis());
    return Duration.ofMillis(boundedDelayMillis);
  }
}
