package io.mopl.domain.directmessage.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "mopl.direct-message.realtime.redis",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class LocalDirectMessageRealtimePublisher implements DirectMessageRealtimePublisher {

  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public void publish(DirectMessageRealtimeEvent event) {
    messagingTemplate.convertAndSend(DirectMessageTopic.of(event.conversationId()), event.message());
  }
}
