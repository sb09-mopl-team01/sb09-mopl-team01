package io.mopl.domain.watchingsession.realtime;

import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscription;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WatchingSessionLeaseRecoveryCoordinator {

  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_RETRY_SCHEDULED = "retry_scheduled";
  private static final String OUTCOME_EXHAUSTED = "exhausted";
  private static final String OUTCOME_CLAIM_ERROR = "claim_error";
  private static final String OUTCOME_COMPLETION_ERROR = "completion_error";
  private static final String OUTCOME_RECORD_ERROR = "record_error";

  private final WatchingSessionLeaseStore leaseStore;
  private final WatchingSessionNodeId nodeId;
  private final WatchingSessionService watchingSessionService;
  private final WatchingSessionLeaseRecoveryMetrics metrics;

  public void recover(WatchingSessionSubscription subscription) {
    String recoveryOwnerId = nodeId.value() + ":" + UUID.randomUUID();
    if (!claim(subscription, recoveryOwnerId)) {
      return;
    }

    try {
      watchingSessionService.endWatchingIfPresent(
          subscription.watcherId(),
          subscription.contentId()
      );
    } catch (RuntimeException databaseException) {
      recordFailure(subscription, recoveryOwnerId, databaseException);
      return;
    }

    try {
      if (leaseStore.completeRecovery(subscription, recoveryOwnerId)) {
        metrics.record(OUTCOME_SUCCESS);
        return;
      }
      metrics.record(OUTCOME_COMPLETION_ERROR);
      log.warn(
          "Watching session lease recovery completion ownership was lost. watcherId={}, contentId={}",
          subscription.watcherId(),
          subscription.contentId()
      );
    } catch (RuntimeException completionException) {
      metrics.record(OUTCOME_COMPLETION_ERROR);
      log.warn(
          "Failed to complete watching session lease recovery. watcherId={}, contentId={}",
          subscription.watcherId(),
          subscription.contentId(),
          completionException
      );
    }
  }

  private boolean claim(WatchingSessionSubscription subscription, String recoveryOwnerId) {
    try {
      return leaseStore.claimRecovery(subscription, recoveryOwnerId);
    } catch (RuntimeException claimException) {
      metrics.record(OUTCOME_CLAIM_ERROR);
      log.warn(
          "Failed to claim watching session lease recovery. watcherId={}, contentId={}",
          subscription.watcherId(),
          subscription.contentId(),
          claimException
      );
      return false;
    }
  }

  private void recordFailure(
      WatchingSessionSubscription subscription,
      String recoveryOwnerId,
      RuntimeException databaseException
  ) {
    try {
      WatchingSessionLeaseRecoveryFailure failure = leaseStore.recordRecoveryFailure(
          subscription,
          recoveryOwnerId
      );
      if (failure.status() == WatchingSessionLeaseRecoveryFailure.Status.RETRY_SCHEDULED) {
        metrics.record(OUTCOME_RETRY_SCHEDULED);
        log.warn(
            "Watching session lease recovery will retry. watcherId={}, contentId={}, attempt={}",
            subscription.watcherId(),
            subscription.contentId(),
            failure.attempt(),
            databaseException
        );
        return;
      }

      if (failure.status() == WatchingSessionLeaseRecoveryFailure.Status.NOT_RECORDED) {
        metrics.record(OUTCOME_RECORD_ERROR);
        log.error(
            "Watching session lease recovery failure was not recorded. watcherId={}, contentId={}",
            subscription.watcherId(),
            subscription.contentId(),
            databaseException
        );
        return;
      }

      metrics.record(OUTCOME_EXHAUSTED);
      log.error(
          "Watching session lease recovery requires operational action. watcherId={}, contentId={}, "
              + "attempt={}, status={}",
          subscription.watcherId(),
          subscription.contentId(),
          failure.attempt(),
          failure.status(),
          databaseException
      );
    } catch (RuntimeException recordException) {
      metrics.record(OUTCOME_RECORD_ERROR);
      log.error(
          "Failed to record watching session lease recovery retry. watcherId={}, contentId={}",
          subscription.watcherId(),
          subscription.contentId(),
          recordException
      );
    }
  }
}
