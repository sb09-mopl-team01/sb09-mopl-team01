package io.mopl.domain.directmessage.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class RedisDirectMessageRealtimePublisherTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final RedisDirectMessageRealtimePublisher publisher =
      new RedisDirectMessageRealtimePublisher(redisTemplate, objectMapper);

  @Test
  void publishesSerializedDirectMessageEventToConfiguredChannel() throws Exception {
    DirectMessageRealtimeEvent event = DirectMessageRealtimeFixtures.event();
    ReflectionTestUtils.setField(publisher, "channel", "direct-message:realtime:test");
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

    publisher.publish(event);

    verify(redisTemplate).convertAndSend(eq("direct-message:realtime:test"), payloadCaptor.capture());
    assertThat(objectMapper.readValue(payloadCaptor.getValue(), DirectMessageRealtimeEvent.class))
        .isEqualTo(event);
  }
}
