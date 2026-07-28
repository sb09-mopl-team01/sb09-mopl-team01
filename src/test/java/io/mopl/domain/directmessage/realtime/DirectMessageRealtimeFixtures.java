package io.mopl.domain.directmessage.realtime;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.user.dto.response.UserSummary;
import java.time.Instant;
import java.util.UUID;

final class DirectMessageRealtimeFixtures {

  private DirectMessageRealtimeFixtures() {
  }

  static DirectMessageRealtimeEvent event() {
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    DirectMessageDto message = new DirectMessageDto(
        UUID.randomUUID(),
        conversationId,
        Instant.parse("2026-07-27T00:00:00Z"),
        UserSummary.builder().userId(senderId).name("sender").build(),
        UserSummary.builder().userId(receiverId).name("receiver").build(),
        "hello"
    );
    return new DirectMessageRealtimeEvent(conversationId, message);
  }
}
