package io.mopl.domain.watchingsession.realtime;

public record WatchingSessionLeaseRecoveryFailure(
    int attempt,
    Status status
) {

  public enum Status {
    RETRY_SCHEDULED,
    EXHAUSTED,
    NOT_RECORDED
  }

  public static WatchingSessionLeaseRecoveryFailure retryScheduled(int attempt) {
    return new WatchingSessionLeaseRecoveryFailure(attempt, Status.RETRY_SCHEDULED);
  }

  public static WatchingSessionLeaseRecoveryFailure exhausted(int attempt) {
    return new WatchingSessionLeaseRecoveryFailure(attempt, Status.EXHAUSTED);
  }

  public static WatchingSessionLeaseRecoveryFailure notRecorded() {
    return new WatchingSessionLeaseRecoveryFailure(0, Status.NOT_RECORDED);
  }
}
