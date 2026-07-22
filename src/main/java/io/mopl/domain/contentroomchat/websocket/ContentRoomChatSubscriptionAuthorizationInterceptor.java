package io.mopl.domain.contentroomchat.websocket;

import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
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
public class ContentRoomChatSubscriptionAuthorizationInterceptor implements ChannelInterceptor {

  private static final Pattern CONTENT_CHAT_SUBSCRIPTION_PATTERN =
      Pattern.compile("^/sub/contents/([^/]+)/chat$");

  private final WatchingSessionRepository watchingSessionRepository;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      return message;
    }

    Matcher matcher = subscriptionMatcher(accessor.getDestination());
    if (!matcher.matches()) {
      return message;
    }

    UUID contentId = parseContentId(matcher.group(1));
    UUID subscriberId = resolveSubscriberId(accessor.getUser());
    if (!watchingSessionRepository.existsByWatcherIdAndContentId(subscriberId, contentId)) {
      throw new AccessDeniedException("현재 콘텐츠 시청 참여자만 채팅을 구독할 수 있습니다.");
    }

    return message;
  }

  private Matcher subscriptionMatcher(String destination) {
    return CONTENT_CHAT_SUBSCRIPTION_PATTERN.matcher(destination == null ? "" : destination);
  }

  private UUID parseContentId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException("올바르지 않은 콘텐츠 채팅 구독 경로입니다.", e);
    }
  }

  private UUID resolveSubscriberId(Principal principal) {
    if (principal instanceof Authentication authentication
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof MoplUserDetails userDetails
        && userDetails.getUser() != null
        && userDetails.getUser().getId() != null) {
      return userDetails.getUser().getId();
    }

    throw new AuthenticationCredentialsNotFoundException("WebSocket 인증 정보가 필요합니다.");
  }
}
