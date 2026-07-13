package io.mopl.domain.watchingsession.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.watchingsession.dto.WatchingSessionChange;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "true")
public class WatchingSessionRedisMessageListener implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    if (message == null || message.getBody() == null || message.getBody().length == 0) {
      log.warn("Ignoring empty watching session Redis message.");
      return;
    }

    WatchingSessionChange change;
    try {
      change = objectMapper.readValue(
          message.getBody(),
          WatchingSessionChange.class
      );
    } catch (IOException e) {
      log.warn("Ignoring malformed watching session Redis message.", e);
      return;
    }

    if (!hasRequiredFields(change)) {
      log.warn("Ignoring watching session Redis message with missing required fields.");
      return;
    }

    try {
      messagingTemplate.convertAndSend(
          WatchingSessionTopic.of(change.watchingSession().content().id()),
          change
      );
    } catch (RuntimeException e) {
      log.error("Failed to relay watching session Redis message to local WebSocket subscribers.", e);
    }
  }

  private boolean hasRequiredFields(WatchingSessionChange change) {
    return change != null
        && change.type() != null
        && change.watchingSession() != null
        && change.watchingSession().content() != null
        && change.watchingSession().content().id() != null;
  }
}
