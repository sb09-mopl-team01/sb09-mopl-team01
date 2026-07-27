package io.mopl.domain.directmessage.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.realtime.DirectMessageRealtimeEvent;
import io.mopl.domain.directmessage.realtime.DirectMessageRealtimePublisher;
import io.mopl.domain.directmessage.service.ConversationService;
import io.mopl.domain.user.dto.response.UserSummary;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DirectMessageWebSocketEventHandlerTest {

  private final ConversationService conversationService = mock(ConversationService.class);
  private final DirectMessageRealtimePublisher realtimePublisher = mock(DirectMessageRealtimePublisher.class);
  private final DirectMessageWebSocketEventHandler handler = new DirectMessageWebSocketEventHandler(
      conversationService,
      realtimePublisher
  );

  @Test
  void handleDirectMessageSentBroadcastsMessageAfterLookup() {
    UUID directMessageId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    DirectMessageSentEvent event = new DirectMessageSentEvent(
        directMessageId,
        conversationId,
        senderId,
        "sender",
        receiverId,
        Instant.parse("2026-07-07T01:00:00Z")
    );
    DirectMessageDto message = new DirectMessageDto(
        directMessageId,
        conversationId,
        Instant.parse("2026-07-07T01:00:00Z"),
        UserSummary.builder()
            .userId(senderId)
            .name("sender")
            .profileImageUrl(null)
            .build(),
        UserSummary.builder()
            .userId(receiverId)
            .name("receiver")
            .profileImageUrl(null)
            .build(),
        "hello"
    );
    when(conversationService.findDirectMessage(directMessageId)).thenReturn(message);

    handler.handleDirectMessageSent(event);

    verify(conversationService).findDirectMessage(directMessageId);
    verify(realtimePublisher).publish(new DirectMessageRealtimeEvent(conversationId, message));
  }
}
