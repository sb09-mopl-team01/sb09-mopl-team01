package io.mopl.domain.directmessage.event;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.realtime.DirectMessageRealtimeEvent;
import io.mopl.domain.directmessage.realtime.DirectMessageRealtimePublisher;
import io.mopl.domain.directmessage.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class DirectMessageWebSocketEventHandler {

  private final ConversationService conversationService;
  private final DirectMessageRealtimePublisher realtimePublisher;

  @Async("directMessageRealtimeExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleDirectMessageSent(DirectMessageSentEvent event) {
    try {
      DirectMessageDto message = conversationService.findDirectMessage(event.directMessageId());
      realtimePublisher.publish(new DirectMessageRealtimeEvent(event.conversationId(), message));
      log.debug("Direct message broadcast. directMessageId={}, conversationId={}",
          event.directMessageId(), event.conversationId());
    } catch (Exception e) {
      log.warn("Direct message broadcast failed. directMessageId={}, conversationId={}",
          event.directMessageId(), event.conversationId(), e);
    }
  }

}
