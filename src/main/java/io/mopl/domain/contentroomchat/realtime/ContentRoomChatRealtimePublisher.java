package io.mopl.domain.contentroomchat.realtime;

public interface ContentRoomChatRealtimePublisher {

  void publish(ContentRoomChatRealtimeEvent event);
}
