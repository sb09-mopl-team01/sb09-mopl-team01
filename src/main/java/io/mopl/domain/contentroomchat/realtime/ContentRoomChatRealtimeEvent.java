package io.mopl.domain.contentroomchat.realtime;

import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import java.util.UUID;

public record ContentRoomChatRealtimeEvent(
    UUID contentId,
    ContentChatDto message
) {
}
