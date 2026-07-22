package io.mopl.domain.watchingsession.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscription;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchingSessionLeaseRecoveryCoordinatorTest {

  private final WatchingSessionLeaseStore leaseStore = mock(WatchingSessionLeaseStore.class);
  private final WatchingSessionService watchingSessionService = mock(WatchingSessionService.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final WatchingSessionLeaseRecoveryCoordinator coordinator =
      new WatchingSessionLeaseRecoveryCoordinator(
          leaseStore,
          new WatchingSessionNodeId("node-a", ""),
          watchingSessionService,
          new WatchingSessionLeaseRecoveryMetrics(meterRegistry)
      );

  @Test
  void databaseFailureSchedulesRetryAndFollowingAttemptCompletesIdempotently() {
    WatchingSessionSubscription subscription = subscription();
    when(leaseStore.claimRecovery(eq(subscription), anyString())).thenReturn(true);
    when(leaseStore.recordRecoveryFailure(eq(subscription), anyString()))
        .thenReturn(WatchingSessionLeaseRecoveryFailure.retryScheduled(1));
    when(leaseStore.completeRecovery(eq(subscription), anyString())).thenReturn(true);
    doThrow(new IllegalStateException("database unavailable"))
        .doNothing()
        .when(watchingSessionService)
        .endWatchingIfPresent(subscription.watcherId(), subscription.contentId());

    coordinator.recover(subscription);
    coordinator.recover(subscription);

    verify(watchingSessionService, times(2)).endWatchingIfPresent(
        subscription.watcherId(), subscription.contentId());
    verify(leaseStore).recordRecoveryFailure(eq(subscription), anyString());
    verify(leaseStore).completeRecovery(eq(subscription), anyString());
    assertThat(metricCount("retry_scheduled")).isEqualTo(1.0);
    assertThat(metricCount("success")).isEqualTo(1.0);
  }

  @Test
  void doesNotEndWatchingWhenRecoveryClaimWasCancelledByActiveLease() {
    WatchingSessionSubscription subscription = subscription();
    when(leaseStore.claimRecovery(eq(subscription), anyString())).thenReturn(false);

    coordinator.recover(subscription);

    verify(watchingSessionService, never()).endWatchingIfPresent(
        subscription.watcherId(), subscription.contentId());
  }

  @Test
  void exhaustedRetryEmitsOperationalMetric() {
    WatchingSessionSubscription subscription = subscription();
    when(leaseStore.claimRecovery(eq(subscription), anyString())).thenReturn(true);
    when(leaseStore.recordRecoveryFailure(eq(subscription), anyString()))
        .thenReturn(WatchingSessionLeaseRecoveryFailure.exhausted(10));
    doThrow(new IllegalStateException("database unavailable"))
        .when(watchingSessionService)
        .endWatchingIfPresent(subscription.watcherId(), subscription.contentId());

    coordinator.recover(subscription);

    assertThat(metricCount("exhausted")).isEqualTo(1.0);
  }

  private double metricCount(String outcome) {
    return meterRegistry.get(WatchingSessionLeaseRecoveryMetrics.METRIC_NAME)
        .tag("outcome", outcome)
        .counter()
        .count();
  }

  private WatchingSessionSubscription subscription() {
    return new WatchingSessionSubscription(UUID.randomUUID(), UUID.randomUUID());
  }
}
