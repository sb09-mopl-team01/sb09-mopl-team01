package io.mopl.domain.contentroomchat.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "mopl.content-room-chat.realtime.redis",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class LocalContentRoomChatRealtimePublisher implements ContentRoomChatRealtimePublisher {

  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public void publish(ContentRoomChatRealtimeEvent event) {
    messagingTemplate.convertAndSend(ContentRoomChatTopic.of(event.contentId()), event.message());
  }
}
