package io.mopl.domain.directmessage.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
public class DirectMessageWebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final DirectMessageSubscriptionAuthorizationInterceptor authorizationInterceptor;

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authorizationInterceptor);
  }
}
