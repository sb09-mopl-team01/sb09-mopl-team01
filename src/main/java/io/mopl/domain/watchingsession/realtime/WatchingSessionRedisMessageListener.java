package io.mopl.domain.watchingsession.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.watchingsession.dto.WatchingSessionChange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

  private static final String WATCHING_SESSION_TOPIC = "/sub/contents/%s/watch";

  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      WatchingSessionChange change = objectMapper.readValue(
          message.getBody(),
          WatchingSessionChange.class
      );
      messagingTemplate.convertAndSend(
          WATCHING_SESSION_TOPIC.formatted(change.watchingSession().content().id()),
          change
      );
    } catch (IOException e) {
      log.error(
          "Ignoring invalid watching session Redis message. payload={}",
          new String(message.getBody(), StandardCharsets.UTF_8),
          e
      );
    }
  }
}
