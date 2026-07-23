package io.mopl.domain.watchingsession.realtime;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mopl.watching-session.redis")
public record WatchingSessionLeaseProperties(
    Duration leaseTtl,
    Duration leaseKeyRetention,
    int recoveryDbTimeoutSeconds,
    Duration recoveryLockTtl,
    Duration recoveryRetention,
    Duration recoveryFailedRetention,
    int recoveryMaxAttempts,
    int recoveryBatchSize,
    Duration recoveryInitialBackoff,
    double recoveryBackoffMultiplier,
    Duration recoveryMaxBackoff
) {

  public WatchingSessionLeaseProperties {
    requirePositive(leaseTtl, "leaseTtl");
    requirePositive(leaseKeyRetention, "leaseKeyRetention");
    if (leaseKeyRetention.compareTo(leaseTtl) <= 0) {
      throw new IllegalArgumentException("WatchingSession leaseKeyRetention은 leaseTtl보다 커야 합니다.");
    }
    if (recoveryDbTimeoutSeconds < 1) {
      throw new IllegalArgumentException("WatchingSession recoveryDbTimeoutSeconds는 1 이상이어야 합니다.");
    }
    requirePositive(recoveryLockTtl, "recoveryLockTtl");
    if (recoveryLockTtl.compareTo(Duration.ofSeconds(recoveryDbTimeoutSeconds)) <= 0) {
      throw new IllegalArgumentException(
          "WatchingSession recoveryLockTtl은 recoveryDbTimeoutSeconds보다 커야 합니다."
      );
    }
    requirePositive(recoveryRetention, "recoveryRetention");
    requirePositive(recoveryFailedRetention, "recoveryFailedRetention");
    if (recoveryMaxAttempts < 1) {
      throw new IllegalArgumentException("WatchingSession recoveryMaxAttempts는 1 이상이어야 합니다.");
    }
    if (recoveryBatchSize < 1) {
      throw new IllegalArgumentException("WatchingSession recoveryBatchSize는 1 이상이어야 합니다.");
    }
    if (recoveryInitialBackoff == null || recoveryInitialBackoff.isNegative()) {
      throw new IllegalArgumentException("WatchingSession recoveryInitialBackoff은 음수일 수 없습니다.");
    }
    if (!Double.isFinite(recoveryBackoffMultiplier) || recoveryBackoffMultiplier < 1.0) {
      throw new IllegalArgumentException("WatchingSession recoveryBackoffMultiplier는 1 이상이어야 합니다.");
    }
    if (recoveryMaxBackoff == null
        || recoveryMaxBackoff.compareTo(recoveryInitialBackoff) < 0) {
      throw new IllegalArgumentException(
          "WatchingSession recoveryMaxBackoff은 recoveryInitialBackoff보다 작을 수 없습니다."
      );
    }
  }

  private static void requirePositive(Duration duration, String propertyName) {
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("WatchingSession " + propertyName + "은 0보다 커야 합니다.");
    }
  }
}
