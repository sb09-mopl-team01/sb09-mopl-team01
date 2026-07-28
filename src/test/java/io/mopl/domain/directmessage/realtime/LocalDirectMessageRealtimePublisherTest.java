package io.mopl.domain.directmessage.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.mopl.global.sse.SseNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class LocalDirectMessageRealtimePublisherTest {

  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final SseNotificationService sseNotificationService = mock(SseNotificationService.class);
  private final LocalDirectMessageRealtimePublisher publisher =
      new LocalDirectMessageRealtimePublisher(messagingTemplate, sseNotificationService);

  @Test
  void relaysDirectMessageToLocalWebSocketSubscribersWhenRedisIsDisabled() {
    DirectMessageRealtimeEvent event = DirectMessageRealtimeFixtures.event();

    publisher.publish(event);

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
  void relaysDirectMessageToSseWhenLocalWebSocketRelayFails() {
    DirectMessageRealtimeEvent event = DirectMessageRealtimeFixtures.event();
    doThrow(new MessageDeliveryException("broker unavailable"))
        .when(messagingTemplate)
        .convertAndSend(eq(DirectMessageTopic.of(event.conversationId())), eq(event.message()));

    publisher.publish(event);

    verify(sseNotificationService).sendDirectMessage(
        event.message().receiver().userId(),
        event.message().id(),
        event.message()
    );
  }

  @Test
  void ignoresInvalidDirectMessageEvent() {
    publisher.publish(null);

    verifyNoInteractions(messagingTemplate, sseNotificationService);
  }
}
