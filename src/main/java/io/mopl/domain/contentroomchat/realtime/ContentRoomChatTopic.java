package io.mopl.domain.contentroomchat.realtime;

import java.util.UUID;

public final class ContentRoomChatTopic {

  private static final String CONTENT_ROOM_CHAT_TOPIC = "/sub/contents/%s/chat";

  private ContentRoomChatTopic() {
  }

  public static String of(UUID contentId) {
    return CONTENT_ROOM_CHAT_TOPIC.formatted(contentId);
  }
}
