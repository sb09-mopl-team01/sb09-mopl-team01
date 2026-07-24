package io.mopl.domain.contentroomchat.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.user.dto.response.UserSummary;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class LocalContentRoomChatRealtimePublisherTest {

  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final LocalContentRoomChatRealtimePublisher publisher =
      new LocalContentRoomChatRealtimePublisher(messagingTemplate);

  @Test
  void relaysChatMessageToLocalWebSocketSubscribersWhenRedisIsDisabled() {
    ContentRoomChatRealtimeEvent event = event();

    publisher.publish(event);

    verify(messagingTemplate).convertAndSend(
        eq(ContentRoomChatTopic.of(event.contentId())),
        eq(event.message())
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
