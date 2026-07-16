package io.mopl.domain.notification.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.notification.event.NotificationCreatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class RedisNotificationPublisherTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final RedisNotificationPublisher publisher =
      new RedisNotificationPublisher(redisTemplate, objectMapper);

  @Test
  void publishesSerializedNotificationEventToConfiguredChannel() throws Exception {
    NotificationCreatedEvent event = NotificationRealtimeFixtures.event();
    ReflectionTestUtils.setField(publisher, "channel", "notification:realtime:test");
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

    publisher.publish(event);

    verify(redisTemplate).convertAndSend(eq("notification:realtime:test"), payloadCaptor.capture());
    assertThat(objectMapper.readValue(payloadCaptor.getValue(), NotificationCreatedEvent.class))
        .isEqualTo(event);
  }
}
