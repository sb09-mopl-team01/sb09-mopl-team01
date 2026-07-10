package io.mopl.domain.watchingsession.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "true")
public class WatchingSessionRedisPubSubConfig {

  private final WatchingSessionRedisMessageListener messageListener;

  @Bean
  public RedisMessageListenerContainer watchingSessionRedisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      @Value("${mopl.watching-session.redis.channel:watching-session:changes}") String channel
  ) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(messageListener, new ChannelTopic(channel));
    return container;
  }
}
