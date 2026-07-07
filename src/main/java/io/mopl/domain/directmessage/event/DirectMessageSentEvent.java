package io.mopl.domain.directmessage.event;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageSentEvent(
    UUID directMessageId,
    UUID conversationId,
    UUID senderId,
    String senderName,
    UUID receiverId,
    Instant occurredAt
) {
}
