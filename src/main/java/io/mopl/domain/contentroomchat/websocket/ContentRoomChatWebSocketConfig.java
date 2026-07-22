package io.mopl.domain.contentroomchat.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
public class ContentRoomChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final ContentRoomChatSubscriptionAuthorizationInterceptor authorizationInterceptor;

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authorizationInterceptor);
  }
}
