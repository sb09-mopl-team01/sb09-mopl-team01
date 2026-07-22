package io.mopl.domain.watchingsession.websocket;

import io.mopl.domain.watchingsession.realtime.WatchingSessionLeaseRecoveryCoordinator;
import io.mopl.domain.watchingsession.realtime.WatchingSessionLeaseStore;
import io.mopl.domain.watchingsession.realtime.WatchingSessionNodeId;
import io.mopl.domain.watchingsession.service.WatchingSessionService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WatchingSessionSubscriptionEventHandler {

  private final WatchingSessionService watchingSessionService;
  private final WatchingSessionSubscriptionRegistry subscriptionRegistry;
  private final WatchingSessionSubscriptionResolver subscriptionResolver;
  private final WatchingSessionLeaseStore leaseStore;
  private final WatchingSessionNodeId nodeId;
  private final WatchingSessionLeaseRecoveryCoordinator recoveryCoordinator;

  @EventListener
  public void handleSubscribe(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    Optional<UUID> contentId = resolveContentId(accessor);
    if (contentId.isEmpty()) {
      return;
    }

    UUID watcherId = resolveWatcherId(accessor);
    if (watcherId == null) {
      return;
    }
    WatchingSessionSubscription subscription = new WatchingSessionSubscription(watcherId, contentId.get());
    boolean firstLocalSubscription = subscriptionRegistry.register(
        accessor.getSessionId(),
        accessor.getSubscriptionId(),
        watcherId,
        contentId.get()
    );
    if (firstLocalSubscription && leaseStore.acquire(subscription, nodeId.value())) {
      try {
        watchingSessionService.startWatchingBySubscription(watcherId, contentId.get());
      } catch (RuntimeException e) {
        subscriptionRegistry.unregister(accessor.getSessionId(), accessor.getSubscriptionId());
        leaseStore.release(subscription, nodeId.value());
        throw e;
      }
    }
  }

  @EventListener
  public void handleUnsubscribe(SessionUnsubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    subscriptionRegistry.unregister(accessor.getSessionId(), accessor.getSubscriptionId())
        .forEach(this::endWatching);
  }

  @EventListener
  public void handleDisconnect(SessionDisconnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    subscriptionRegistry.unregisterSession(accessor.getSessionId())
        .forEach(this::endWatching);
  }

  private void endWatching(WatchingSessionSubscription subscription) {
    if (leaseStore.release(subscription, nodeId.value())) {
      recoveryCoordinator.recover(subscription);
    }
  }

  private Optional<UUID> resolveContentId(StompHeaderAccessor accessor) {
    try {
      return subscriptionResolver.resolveContentId(accessor.getDestination());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid watching session subscription destination. destination={}",
          accessor.getDestination());
      return Optional.empty();
    }
  }

  private UUID resolveWatcherId(StompHeaderAccessor accessor) {
    try {
      return subscriptionResolver.resolveWatcherId(accessor.getUser());
    } catch (AuthenticationException e) {
      log.warn("Watching session subscription ignored because user is not authenticated. sessionId={}",
          accessor.getSessionId());
      return null;
    }
  }
}
