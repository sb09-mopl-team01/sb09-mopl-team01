package io.mopl.domain.contentroomchat.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "mopl.content-room-chat.realtime.redis",
    name = "enabled",
    havingValue = "true"
)
public class ContentRoomChatRedisMessageListener implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    if (message == null || message.getBody() == null || message.getBody().length == 0) {
      log.warn("Ignoring empty content room chat Redis message.");
      return;
    }

    ContentRoomChatRealtimeEvent event;
    try {
      event = objectMapper.readValue(message.getBody(), ContentRoomChatRealtimeEvent.class);
    } catch (IOException e) {
      log.warn("Ignoring malformed content room chat Redis message.", e);
      return;
    }

    if (!hasRequiredFields(event)) {
      log.warn("Ignoring content room chat Redis message with missing required fields.");
      return;
    }

    try {
      messagingTemplate.convertAndSend(ContentRoomChatTopic.of(event.contentId()), event.message());
    } catch (RuntimeException e) {
      log.error(
          "Failed to relay content room chat Redis message to local WebSocket subscribers. contentId={}",
          event.contentId(),
          e
      );
    }
  }

  private boolean hasRequiredFields(ContentRoomChatRealtimeEvent event) {
    if (event == null || event.contentId() == null || event.message() == null) {
      return false;
    }

    ContentChatDto message = event.message();
    return message.sender() != null && StringUtils.hasText(message.content());
  }
}
