package io.mopl.domain.directmessage.realtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.global.sse.SseNotificationService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class DirectMessageRedisMessageListenerTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final SseNotificationService sseNotificationService = mock(SseNotificationService.class);
  private final DirectMessageRedisMessageListener listener =
      new DirectMessageRedisMessageListener(
          objectMapper,
          messagingTemplate,
          sseNotificationService
      );

  @Test
  void relaysRedisMessageToLocalWebSocketSubscribers() throws Exception {
    DirectMessageRealtimeEvent event = DirectMessageRealtimeFixtures.event();

    listener.onMessage(message(objectMapper.writeValueAsString(event)), null);

    verify(messagingTemplate).convertAndSend(
        eq(DirectMessageTopic.of(event.conversationId())),
        eq(event.message())
    );
    verify(sseNotificationService).sendDirectMessage(
        event.message().receiver().userId(),
        event.message().id(),
        event.message()
    );
  }

  @Test
  void ignoresMalformedMessage() {
    listener.onMessage(message("not-json"), null);

    verifyNoInteractions(messagingTemplate, sseNotificationService);
  }

  @Test
  void ignoresMessageWithMissingRequiredFields() {
    listener.onMessage(message("{}"), null);

    verifyNoInteractions(messagingTemplate, sseNotificationService);
  }

  @Test
  void isolatesLocalWebSocketRelayFailure() throws Exception {
    DirectMessageRealtimeEvent event = DirectMessageRealtimeFixtures.event();
    doThrow(new MessageDeliveryException("broker unavailable"))
        .when(messagingTemplate)
        .convertAndSend(eq(DirectMessageTopic.of(event.conversationId())), eq(event.message()));

    assertThatCode(() -> listener.onMessage(message(objectMapper.writeValueAsString(event)), null))
        .doesNotThrowAnyException();
    verify(sseNotificationService).sendDirectMessage(
        event.message().receiver().userId(),
        event.message().id(),
        event.message()
    );
  }

  @Test
  void isolatesLocalSseRelayFailure() throws Exception {
    DirectMessageRealtimeEvent event = DirectMessageRealtimeFixtures.event();
    doThrow(new IllegalStateException("SSE unavailable"))
        .when(sseNotificationService)
        .sendDirectMessage(
            event.message().receiver().userId(),
            event.message().id(),
            event.message()
        );

    assertThatCode(() -> listener.onMessage(message(objectMapper.writeValueAsString(event)), null))
        .doesNotThrowAnyException();
    verify(messagingTemplate).convertAndSend(
        eq(DirectMessageTopic.of(event.conversationId())),
        eq(event.message())
    );
  }

  private DefaultMessage message(String payload) {
    return new DefaultMessage(
        "direct-message:realtime".getBytes(StandardCharsets.UTF_8),
        payload.getBytes(StandardCharsets.UTF_8)
    );
  }
}
