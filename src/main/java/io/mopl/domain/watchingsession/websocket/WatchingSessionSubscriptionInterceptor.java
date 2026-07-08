package io.mopl.domain.watchingsession.websocket;

import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.global.security.MoplUserDetails;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WatchingSessionSubscriptionInterceptor implements ChannelInterceptor {

  private static final Pattern WATCH_SUBSCRIPTION_PATTERN =
      Pattern.compile("^/sub/contents/([^/]+)/watch$");

  private final WatchingSessionService watchingSessionService;
  private final WatchingSessionSubscriptionRegistry subscriptionRegistry;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      handleSubscribe(accessor);
    } else if (StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())) {
      handleUnsubscribe(accessor);
    } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
      handleDisconnect(accessor);
    }

    return message;
  }

  private void handleSubscribe(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    Matcher matcher = WATCH_SUBSCRIPTION_PATTERN.matcher(destination == null ? "" : destination);
    if (!matcher.matches()) {
      return;
    }

    UUID contentId = parseContentId(matcher.group(1));
    UUID watcherId = resolveWatcherId(accessor.getUser());
    watchingSessionService.startWatchingBySubscription(watcherId, contentId);
    subscriptionRegistry.register(
        accessor.getSessionId(),
        accessor.getSubscriptionId(),
        watcherId,
        contentId
    );
  }

  private void handleUnsubscribe(StompHeaderAccessor accessor) {
    subscriptionRegistry.unregister(accessor.getSessionId(), accessor.getSubscriptionId())
        .forEach(this::endWatching);
  }

  private void handleDisconnect(StompHeaderAccessor accessor) {
    subscriptionRegistry.unregisterSession(accessor.getSessionId())
        .forEach(this::endWatching);
  }

  private void endWatching(WatchingSessionSubscription subscription) {
    watchingSessionService.endWatchingIfPresent(subscription.watcherId(), subscription.contentId());
  }

  private UUID parseContentId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException("올바르지 않은 콘텐츠 시청 세션 구독 경로입니다.", e);
    }
  }

  private UUID resolveWatcherId(Principal principal) {
    if (principal instanceof Authentication authentication
        && authentication.getPrincipal() instanceof MoplUserDetails userDetails
        && userDetails.getUser() != null
        && userDetails.getUser().getId() != null) {
      return userDetails.getUser().getId();
    }

    throw new AuthenticationCredentialsNotFoundException("WebSocket 인증 정보가 필요합니다.");
  }
}
