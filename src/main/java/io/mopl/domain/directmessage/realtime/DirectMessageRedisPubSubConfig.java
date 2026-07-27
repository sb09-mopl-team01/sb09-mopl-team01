package io.mopl.domain.directmessage.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "mopl.direct-message.realtime.redis",
    name = "enabled",
    havingValue = "true"
)
public class DirectMessageRedisPubSubConfig {

  private final DirectMessageRedisMessageListener messageListener;

  @Bean
  public RedisMessageListenerContainer directMessageRedisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      @Value("${mopl.direct-message.realtime.redis.channel:direct-message:realtime}") String channel
  ) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(messageListener, new ChannelTopic(channel));
    return container;
  }
}
