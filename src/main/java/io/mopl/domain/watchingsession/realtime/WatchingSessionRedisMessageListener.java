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
    try {
      WatchingSessionChange change = objectMapper.readValue(
          message.getBody(),
          WatchingSessionChange.class
      );
      validate(change);
      messagingTemplate.convertAndSend(
          WatchingSessionTopic.of(change.watchingSession().content().id()),
          change
      );
    } catch (IOException | RuntimeException e) {
      log.warn("Ignoring invalid watching session Redis message.", e);
    }
  }

  private void validate(WatchingSessionChange change) {
    if (change == null
        || change.type() == null
        || change.watchingSession() == null
        || change.watchingSession().content() == null
        || change.watchingSession().content().id() == null) {
      throw new IllegalArgumentException("시청 세션 변경 메시지에 필수 값이 없습니다.");
    }
  }
}
