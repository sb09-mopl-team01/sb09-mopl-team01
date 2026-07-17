package io.mopl.domain.notification.realtime;

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
    prefix = "mopl.notification.realtime.redis",
    name = "enabled",
    havingValue = "true"
)
public class NotificationRedisPubSubConfig {

  private final RedisNotificationListener redisNotificationListener;

  @Bean
  public RedisMessageListenerContainer notificationRedisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      @Value("${mopl.notification.realtime.redis.channel:notification:realtime}") String channel
  ) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(redisNotificationListener, new ChannelTopic(channel));
    return container;
  }
}
