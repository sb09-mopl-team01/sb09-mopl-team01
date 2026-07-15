package io.mopl.infra.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mopl.outbox")
public record OutboxProperties(
    boolean relayEnabled,
    Duration relayDelay,
    int batchSize,
    Duration claimTimeout,
    int maxAttempts,
    Duration initialBackoff,
    double backoffMultiplier,
    Duration maxBackoff,
    Duration publishedRetention,
    Duration cleanupDelay,
    Duration sendTimeout
) {

  public OutboxProperties {
    if (relayDelay == null || relayDelay.isNegative() || relayDelay.isZero()) {
      throw new IllegalArgumentException("Outbox relayDelay는 0보다 커야 합니다.");
    }
    if (batchSize < 1) {
      throw new IllegalArgumentException("Outbox batchSize는 1 이상이어야 합니다.");
    }
    if (claimTimeout == null || claimTimeout.isNegative() || claimTimeout.isZero()) {
      throw new IllegalArgumentException("Outbox claimTimeout은 0보다 커야 합니다.");
    }
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("Outbox maxAttempts는 1 이상이어야 합니다.");
    }
    if (initialBackoff == null || initialBackoff.isNegative()) {
      throw new IllegalArgumentException("Outbox initialBackoff은 음수일 수 없습니다.");
    }
    if (!Double.isFinite(backoffMultiplier) || backoffMultiplier < 1.0) {
      throw new IllegalArgumentException("Outbox backoffMultiplier는 1 이상이어야 합니다.");
    }
    if (maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0) {
      throw new IllegalArgumentException("Outbox maxBackoff은 initialBackoff보다 작을 수 없습니다.");
    }
    if (publishedRetention == null || publishedRetention.isNegative() || publishedRetention.isZero()) {
      throw new IllegalArgumentException("Outbox publishedRetention은 0보다 커야 합니다.");
    }
    if (cleanupDelay == null || cleanupDelay.isNegative() || cleanupDelay.isZero()) {
      throw new IllegalArgumentException("Outbox cleanupDelay는 0보다 커야 합니다.");
    }
    if (sendTimeout == null || sendTimeout.isNegative() || sendTimeout.isZero()) {
      throw new IllegalArgumentException("Outbox sendTimeout은 0보다 커야 합니다.");
    }
  }
}
