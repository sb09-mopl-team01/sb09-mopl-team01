package io.mopl.domain.contentroomchat.realtime;

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
    prefix = "mopl.content-room-chat.realtime.redis",
    name = "enabled",
    havingValue = "true"
)
public class ContentRoomChatRedisPubSubConfig {

  private final ContentRoomChatRedisMessageListener messageListener;

  @Bean
  public RedisMessageListenerContainer contentRoomChatRedisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      @Value("${mopl.content-room-chat.realtime.redis.channel:content-room-chat:realtime}") String channel
  ) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(messageListener, new ChannelTopic(channel));
    return container;
  }
}
