package io.mopl.domain.contentroomchat.realtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.user.dto.response.UserSummary;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class ContentRoomChatRedisMessageListenerTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final ContentRoomChatRedisMessageListener listener =
      new ContentRoomChatRedisMessageListener(objectMapper, messagingTemplate);

  @Test
  void relaysRedisMessageToLocalWebSocketSubscribers() throws Exception {
    ContentRoomChatRealtimeEvent event = event();

    listener.onMessage(message(objectMapper.writeValueAsString(event)), null);

    verify(messagingTemplate).convertAndSend(
        eq(ContentRoomChatTopic.of(event.contentId())),
        eq(event.message())
    );
  }

  @Test
  void ignoresMalformedMessage() {
    listener.onMessage(message("not-json"), null);

    verifyNoInteractions(messagingTemplate);
  }

  @Test
  void ignoresMessageWithMissingRequiredFields() {
    listener.onMessage(message("{}"), null);

    verifyNoInteractions(messagingTemplate);
  }

  @Test
  void isolatesLocalWebSocketRelayFailure() throws Exception {
    ContentRoomChatRealtimeEvent event = event();
    doThrow(new MessageDeliveryException("broker unavailable"))
        .when(messagingTemplate)
        .convertAndSend(eq(ContentRoomChatTopic.of(event.contentId())), eq(event.message()));

    assertThatCode(() -> listener.onMessage(message(objectMapper.writeValueAsString(event)), null))
        .doesNotThrowAnyException();
  }

  private DefaultMessage message(String payload) {
    return new DefaultMessage(
        "content-room-chat:realtime".getBytes(StandardCharsets.UTF_8),
        payload.getBytes(StandardCharsets.UTF_8)
    );
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
