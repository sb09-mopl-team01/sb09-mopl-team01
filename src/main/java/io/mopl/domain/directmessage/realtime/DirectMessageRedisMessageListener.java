package io.mopl.domain.directmessage.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.directmessage.dto.DirectMessageDto;
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
    prefix = "mopl.direct-message.realtime.redis",
    name = "enabled",
    havingValue = "true"
)
public class DirectMessageRedisMessageListener implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    if (message == null || message.getBody() == null || message.getBody().length == 0) {
      log.warn("Ignoring empty direct message Redis message.");
      return;
    }

    DirectMessageRealtimeEvent event;
    try {
      event = objectMapper.readValue(message.getBody(), DirectMessageRealtimeEvent.class);
    } catch (IOException e) {
      log.warn("Ignoring malformed direct message Redis message.", e);
      return;
    }

    if (!hasRequiredFields(event)) {
      log.warn("Ignoring direct message Redis message with missing required fields.");
      return;
    }

    try {
      messagingTemplate.convertAndSend(DirectMessageTopic.of(event.conversationId()), event.message());
    } catch (RuntimeException e) {
      log.error("Failed to relay direct message Redis event to local WebSocket subscribers. conversationId={}",
          event.conversationId(), e);
    }
  }

  private boolean hasRequiredFields(DirectMessageRealtimeEvent event) {
    if (event == null || event.conversationId() == null || event.message() == null) {
      return false;
    }

    DirectMessageDto message = event.message();
    return message.id() != null
        && event.conversationId().equals(message.conversationId())
        && message.sender() != null
        && message.receiver() != null
        && StringUtils.hasText(message.content());
  }
}
