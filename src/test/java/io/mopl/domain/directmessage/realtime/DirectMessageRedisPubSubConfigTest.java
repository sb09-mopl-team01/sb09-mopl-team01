package io.mopl.domain.directmessage.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class DirectMessageRedisPubSubConfigTest {

  @Test
  void registersDirectMessageListenerWithConfiguredConnectionFactory() {
    RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    DirectMessageRedisPubSubConfig config = new DirectMessageRedisPubSubConfig(
        mock(DirectMessageRedisMessageListener.class)
    );

    var container = config.directMessageRedisMessageListenerContainer(
        connectionFactory,
        "direct-message:realtime:test"
    );

    assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
  }
}
