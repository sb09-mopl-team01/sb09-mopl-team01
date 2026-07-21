package io.mopl.domain.watchingsession.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscription;
import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscriptionRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchingSessionLeaseMaintenanceTest {

  private final WatchingSessionSubscriptionRegistry subscriptionRegistry =
      new WatchingSessionSubscriptionRegistry();
  private final WatchingSessionLeaseStore leaseStore = mock(WatchingSessionLeaseStore.class);
  private final WatchingSessionNodeId nodeId = new WatchingSessionNodeId("node-a", "");
  private final WatchingSessionService watchingSessionService = mock(WatchingSessionService.class);
  private final WatchingSessionLeaseMaintenance maintenance = new WatchingSessionLeaseMaintenance(
      subscriptionRegistry,
      leaseStore,
      nodeId,
      watchingSessionService
  );

  @Test
  void endsWatchingWhenExpiredLeaseWasGlobalLastLease() {
    WatchingSessionSubscription subscription = subscription();
    when(leaseStore.expireStaleLeases()).thenReturn(List.of(subscription));

    maintenance.maintainLeases();

    verify(watchingSessionService).endWatchingIfPresent(
        subscription.watcherId(), subscription.contentId());
  }

  @Test
  void reacquiresLeaseAndRestartsWatchingWhenLocalLeaseWasLost() {
    WatchingSessionSubscription subscription = subscription();
    subscriptionRegistry.register("session-1", "sub-1", subscription.watcherId(), subscription.contentId());
    when(leaseStore.refresh(subscription, "node-a")).thenReturn(false);
    when(leaseStore.acquire(subscription, "node-a")).thenReturn(true);
    when(leaseStore.expireStaleLeases()).thenReturn(List.of());

    maintenance.maintainLeases();

    verify(watchingSessionService).startWatchingBySubscription(
        subscription.watcherId(), subscription.contentId());
  }

  private WatchingSessionSubscription subscription() {
    return new WatchingSessionSubscription(UUID.randomUUID(), UUID.randomUUID());
  }
}
