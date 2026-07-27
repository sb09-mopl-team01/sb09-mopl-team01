package io.mopl.domain.directmessage.realtime;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import java.util.UUID;

public record DirectMessageRealtimeEvent(
    UUID conversationId,
    DirectMessageDto message
) {
}
