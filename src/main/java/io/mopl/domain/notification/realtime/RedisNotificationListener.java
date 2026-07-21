package io.mopl.domain.notification.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.notification.event.NotificationCreatedEvent;
import io.mopl.global.sse.SseNotificationService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "mopl.notification.realtime.redis",
    name = "enabled",
    havingValue = "true"
)
public class RedisNotificationListener implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SseNotificationService sseNotificationService;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    if (message == null || message.getBody() == null || message.getBody().length == 0) {
      log.warn("Ignoring empty notification realtime Redis message.");
      return;
    }

    NotificationCreatedEvent event;
    try {
      event = objectMapper.readValue(message.getBody(), NotificationCreatedEvent.class);
    } catch (IOException e) {
      log.warn("Ignoring malformed notification realtime Redis message.", e);
      return;
    }

    if (!hasRequiredFields(event)) {
      log.warn("Ignoring notification realtime Redis message with missing required fields.");
      return;
    }

    try {
      sseNotificationService.sendNotification(
          event.receiverId(),
          event.notificationId(),
          event.notification()
      );
    } catch (RuntimeException e) {
      log.error(
          "Failed to relay notification Redis message to local SSE connections. notificationId={}, receiverId={}",
          event.notificationId(),
          event.receiverId(),
          e
      );
    }
  }

  private boolean hasRequiredFields(NotificationCreatedEvent event) {
    return event != null
        && event.notificationId() != null
        && event.receiverId() != null
        && event.notification() != null;
  }
}
