package io.mopl.domain.notification.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class NotificationRedisPubSubConfigTest {

  @Test
  void registersNotificationListenerWithConfiguredConnectionFactory() {
    RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    NotificationRedisPubSubConfig config = new NotificationRedisPubSubConfig(
        mock(RedisNotificationListener.class)
    );

    var container = config.notificationRedisMessageListenerContainer(
        connectionFactory,
        "notification:realtime:test"
    );

    assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
  }
}
