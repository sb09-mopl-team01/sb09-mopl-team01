package io.mopl.domain.watchingsession.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
public class WatchingSessionWebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final WatchingSessionSubscriptionInterceptor watchingSessionSubscriptionInterceptor;

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(watchingSessionSubscriptionInterceptor);
  }
}
