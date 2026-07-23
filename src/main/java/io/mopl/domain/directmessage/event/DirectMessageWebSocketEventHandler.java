package io.mopl.domain.directmessage.event;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.service.ConversationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class DirectMessageWebSocketEventHandler {

  private static final String DIRECT_MESSAGE_TOPIC = "/sub/conversations/%s/direct-messages";

  private final ConversationService conversationService;
  private final SimpMessagingTemplate messagingTemplate;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleDirectMessageSent(DirectMessageSentEvent event) {
    try {
      DirectMessageDto message = conversationService.findDirectMessage(event.directMessageId());
      messagingTemplate.convertAndSend(topic(event.conversationId()), message);
      log.debug("Direct message broadcast. directMessageId={}, conversationId={}",
          event.directMessageId(), event.conversationId());
    } catch (Exception e) {
      log.warn("Direct message broadcast failed. directMessageId={}, conversationId={}",
          event.directMessageId(), event.conversationId(), e);
    }
  }

  private String topic(UUID conversationId) {
    return DIRECT_MESSAGE_TOPIC.formatted(conversationId);
  }
}
