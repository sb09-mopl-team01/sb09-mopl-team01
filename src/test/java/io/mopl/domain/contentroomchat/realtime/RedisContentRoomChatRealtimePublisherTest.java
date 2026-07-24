package io.mopl.domain.contentroomchat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.user.dto.response.UserSummary;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class RedisContentRoomChatRealtimePublisherTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final RedisContentRoomChatRealtimePublisher publisher =
      new RedisContentRoomChatRealtimePublisher(redisTemplate, objectMapper);

  @Test
  void publishesSerializedContentChatEventToConfiguredChannel() throws Exception {
    ContentRoomChatRealtimeEvent event = event();
    ReflectionTestUtils.setField(publisher, "channel", "content-room-chat:realtime:test");
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

    publisher.publish(event);

    verify(redisTemplate).convertAndSend(eq("content-room-chat:realtime:test"), payloadCaptor.capture());
    assertThat(objectMapper.readValue(payloadCaptor.getValue(), ContentRoomChatRealtimeEvent.class))
        .isEqualTo(event);
  }

  private ContentRoomChatRealtimeEvent event() {
    return new ContentRoomChatRealtimeEvent(
        UUID.randomUUID(),
        new ContentChatDto(
            UserSummary.builder().userId(UUID.randomUUID()).name("sender").build(),
            "hello"
        )
    );
  }
}
