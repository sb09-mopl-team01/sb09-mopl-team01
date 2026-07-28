package io.mopl.domain.directmessage.realtime;

public interface DirectMessageRealtimePublisher {

  void publish(DirectMessageRealtimeEvent event);
}
