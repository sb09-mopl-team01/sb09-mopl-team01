package io.mopl.domain.watchingsession.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WatchingSessionSubscriptionInterceptor implements ChannelInterceptor {

  private final WatchingSessionSubscriptionResolver subscriptionResolver;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      validateSubscribe(accessor);
    }

    return message;
  }

  private void validateSubscribe(StompHeaderAccessor accessor) {
    try {
      subscriptionResolver.resolveContentId(accessor.getDestination())
          .ifPresent(contentId -> subscriptionResolver.resolveWatcherId(accessor.getUser()));
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException("올바르지 않은 콘텐츠 시청 세션 구독 경로입니다.", e);
    }
  }
}
