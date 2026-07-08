package io.mopl.domain.watchingsession.websocket;

import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.global.security.MoplUserDetails;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Component
@RequiredArgsConstructor
public class WatchingSessionSubscriptionEventHandler {

  private static final Pattern WATCH_SUBSCRIPTION_PATTERN =
      Pattern.compile("^/sub/contents/([^/]+)/watch$");

  private final WatchingSessionService watchingSessionService;
  private final WatchingSessionSubscriptionRegistry subscriptionRegistry;

  @EventListener
  public void handleSubscribe(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String destination = accessor.getDestination();
    Matcher matcher = WATCH_SUBSCRIPTION_PATTERN.matcher(destination == null ? "" : destination);
    if (!matcher.matches()) {
      return;
    }

    UUID contentId = parseContentId(matcher.group(1));
    UUID watcherId = resolveWatcherId(accessor.getUser());
    subscriptionRegistry.register(
        accessor.getSessionId(),
        accessor.getSubscriptionId(),
        watcherId,
        contentId
    );
    watchingSessionService.startWatchingBySubscription(watcherId, contentId);
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
    watchingSessionService.endWatchingIfPresent(subscription.watcherId(), subscription.contentId());
  }

  private UUID parseContentId(String value) {
    return UUID.fromString(value);
  }

  private UUID resolveWatcherId(Principal principal) {
    if (principal instanceof Authentication authentication
        && authentication.getPrincipal() instanceof MoplUserDetails userDetails
        && userDetails.getUser() != null
        && userDetails.getUser().getId() != null) {
      return userDetails.getUser().getId();
    }

    throw new IllegalStateException("WebSocket 인증 정보가 필요합니다.");
  }
}
