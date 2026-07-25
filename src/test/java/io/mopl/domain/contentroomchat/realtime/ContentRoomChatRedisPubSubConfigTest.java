package io.mopl.domain.contentroomchat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class ContentRoomChatRedisPubSubConfigTest {

  @Test
  void registersContentRoomChatListenerWithConfiguredConnectionFactory() {
    RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    ContentRoomChatRedisPubSubConfig config = new ContentRoomChatRedisPubSubConfig(
        mock(ContentRoomChatRedisMessageListener.class)
    );

    var container = config.contentRoomChatRedisMessageListenerContainer(
        connectionFactory,
        "content-room-chat:test"
    );

    assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
  }
}
