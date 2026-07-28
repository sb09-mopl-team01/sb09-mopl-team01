package io.mopl.domain.directmessage.realtime;

import io.mopl.global.sse.SseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "mopl.direct-message.realtime.redis",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class LocalDirectMessageRealtimePublisher implements DirectMessageRealtimePublisher {

  private final SimpMessagingTemplate messagingTemplate;
  private final SseNotificationService sseNotificationService;

  @Override
  public void publish(DirectMessageRealtimeEvent event) {
    if (!hasRequiredFields(event)) {
      log.warn("Ignoring local direct message realtime event with missing required fields.");
      return;
    }

    try {
      messagingTemplate.convertAndSend(DirectMessageTopic.of(event.conversationId()), event.message());
    } catch (RuntimeException e) {
      log.error(
          "Failed to relay direct message to local WebSocket subscribers. conversationId={}",
          event.conversationId(),
          e
      );
    }

    try {
      sseNotificationService.sendDirectMessage(
          event.message().receiver().userId(),
          event.message().id(),
          event.message()
      );
    } catch (RuntimeException e) {
      log.error(
          "Failed to relay direct message to local SSE subscriber. conversationId={}",
          event.conversationId(),
          e
      );
    }
  }

  private boolean hasRequiredFields(DirectMessageRealtimeEvent event) {
    return event != null
        && event.conversationId() != null
        && event.message() != null
        && event.message().id() != null
        && event.conversationId().equals(event.message().conversationId())
        && event.message().receiver() != null
        && event.message().receiver().userId() != null;
  }
}
