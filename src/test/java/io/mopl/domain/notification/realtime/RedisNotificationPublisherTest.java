package io.mopl.domain.notification.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
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

  @Test
  void ignoresSerializationFailureWithoutPublishing() throws Exception {
    ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
    RedisNotificationPublisher failingPublisher =
        new RedisNotificationPublisher(redisTemplate, failingObjectMapper);
    NotificationCreatedEvent event = NotificationRealtimeFixtures.event();
    given(failingObjectMapper.writeValueAsString(event))
        .willThrow(new JsonProcessingException("serialization failed") {});

    failingPublisher.publish(event);

    verifyNoInteractions(redisTemplate);
  }

  @Test
  void isolatesRedisPublishFailure() throws Exception {
    NotificationCreatedEvent event = NotificationRealtimeFixtures.event();
    ReflectionTestUtils.setField(publisher, "channel", "notification:realtime:test");
    given(redisTemplate.convertAndSend("notification:realtime:test", objectMapper.writeValueAsString(event)))
        .willThrow(new IllegalStateException("Redis unavailable"));

    publisher.publish(event);
  }
}
