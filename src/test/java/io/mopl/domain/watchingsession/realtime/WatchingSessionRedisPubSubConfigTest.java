package io.mopl.domain.watchingsession.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class WatchingSessionRedisPubSubConfigTest {

  @Test
  void registersWatchingSessionListenerWithConfiguredConnectionFactory() {
    RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    WatchingSessionRedisPubSubConfig config = new WatchingSessionRedisPubSubConfig(
        mock(WatchingSessionRedisMessageListener.class)
    );

    var container = config.watchingSessionRedisMessageListenerContainer(
        connectionFactory,
        "watching-session:test"
    );

    assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
  }
}
