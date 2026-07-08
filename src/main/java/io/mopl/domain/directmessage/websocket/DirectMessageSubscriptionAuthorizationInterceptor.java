package io.mopl.domain.directmessage.websocket;

import io.mopl.domain.directmessage.repository.ConversationRepository;
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
public class DirectMessageSubscriptionAuthorizationInterceptor implements ChannelInterceptor {

  private static final Pattern DIRECT_MESSAGE_SUBSCRIPTION_PATTERN =
      Pattern.compile("^/sub/conversations/([^/]+)/direct-messages$");

  private final ConversationRepository conversationRepository;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      return message;
    }

    String destination = accessor.getDestination();
    Matcher matcher = DIRECT_MESSAGE_SUBSCRIPTION_PATTERN.matcher(destination == null ? "" : destination);
    if (!matcher.matches()) {
      return message;
    }

    UUID conversationId = parseConversationId(matcher.group(1));
    UUID subscriberId = resolveSubscriberId(accessor.getUser());
    if (!conversationRepository.existsByIdAndParticipantId(conversationId, subscriberId)) {
      throw new AccessDeniedException("DM 대화 참여자만 구독할 수 있습니다.");
    }

    return message;
  }

  private UUID parseConversationId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException("올바르지 않은 DM 구독 경로입니다.", e);
    }
  }

  private UUID resolveSubscriberId(Principal principal) {
    if (principal instanceof Authentication authentication
        && authentication.getPrincipal() instanceof MoplUserDetails userDetails
        && userDetails.getUser() != null
        && userDetails.getUser().getId() != null) {
      return userDetails.getUser().getId();
    }

    throw new AuthenticationCredentialsNotFoundException("WebSocket 인증 정보가 필요합니다.");
  }
}
