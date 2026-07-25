package io.mopl.domain.watchingsession.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscription;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoOpWatchingSessionRedisStoreTest {

  private final WatchingSessionSubscription subscription = new WatchingSessionSubscription(
      UUID.randomUUID(),
      UUID.randomUUID()
  );

  @Test
  void keepsWatchingSessionOperationsAvailableWhenRedisIsDisabled() {
    NoOpWatchingSessionLeaseStore leaseStore = new NoOpWatchingSessionLeaseStore();
    NoOpWatchingSessionPresenceStore presenceStore = new NoOpWatchingSessionPresenceStore();

    assertThat(leaseStore.acquire(subscription, "local-node")).isTrue();
    assertThat(leaseStore.refresh(subscription, "local-node")).isTrue();
    assertThat(leaseStore.release(subscription, "local-node")).isTrue();
    assertThat(leaseStore.expireStaleLeases()).isEmpty();
    assertThat(leaseStore.claimRecovery(subscription, "local-node")).isTrue();
    assertThat(leaseStore.completeRecovery(subscription, "local-node")).isTrue();
    assertThat(leaseStore.recordRecoveryFailure(subscription, "local-node").status())
        .isEqualTo(WatchingSessionLeaseRecoveryFailure.Status.NOT_RECORDED);
    assertThatCode(() -> {
      presenceStore.enter(subscription.watcherId(), subscription.contentId());
      presenceStore.leave(subscription.watcherId(), subscription.contentId());
    }).doesNotThrowAnyException();
  }
}
