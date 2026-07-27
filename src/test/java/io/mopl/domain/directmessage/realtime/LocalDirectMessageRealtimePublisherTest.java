package io.mopl.domain.directmessage.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class LocalDirectMessageRealtimePublisherTest {

  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final LocalDirectMessageRealtimePublisher publisher =
      new LocalDirectMessageRealtimePublisher(messagingTemplate);

  @Test
  void relaysDirectMessageToLocalWebSocketSubscribersWhenRedisIsDisabled() {
    DirectMessageRealtimeEvent event = DirectMessageRealtimeFixtures.event();

    publisher.publish(event);

    verify(messagingTemplate).convertAndSend(
        eq(DirectMessageTopic.of(event.conversationId())),
        eq(event.message())
    );
  }
}
