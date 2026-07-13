package io.mopl.domain.watchingsession.realtime;

import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "true")
public class WatchingSessionLeaseMaintenance {

  private final WatchingSessionSubscriptionRegistry subscriptionRegistry;
  private final WatchingSessionLeaseStore leaseStore;
  private final WatchingSessionNodeId nodeId;
  private final WatchingSessionService watchingSessionService;

  @Scheduled(fixedDelayString = "${mopl.watching-session.redis.lease-maintenance-delay-millis:30000}")
  public void maintainLeases() {
    subscriptionRegistry.activeSubscriptions().forEach(subscription -> {
      try {
        if (!leaseStore.refresh(subscription, nodeId.value())
            && leaseStore.acquire(subscription, nodeId.value())) {
          watchingSessionService.startWatchingBySubscription(
              subscription.watcherId(),
              subscription.contentId()
          );
        }
      } catch (RuntimeException e) {
        log.warn("Failed to refresh watching session lease. watcherId={}, contentId={}",
            subscription.watcherId(), subscription.contentId(), e);
      }
    });

    leaseStore.expireStaleLeases().forEach(subscription -> {
      try {
        watchingSessionService.endWatchingIfPresent(subscription.watcherId(), subscription.contentId());
      } catch (RuntimeException e) {
        log.warn("Failed to end expired watching session. watcherId={}, contentId={}",
            subscription.watcherId(), subscription.contentId(), e);
      }
    });
  }
}
