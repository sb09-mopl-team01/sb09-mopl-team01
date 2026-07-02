package io.mopl.domain.watchingsession.event;

import io.mopl.domain.watchingsession.dto.WatchingSessionEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WatchingSessionWebSocketEventHandler {

  private static final String WATCHING_SESSION_TOPIC = "/sub/contents/%s/watching-sessions";

  private final SimpMessagingTemplate messagingTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleEntered(WatchingSessionEnteredEvent event) {
    messagingTemplate.convertAndSend(
        topic(event),
        new WatchingSessionEventMessage(
            "ENTERED",
            event.sessionId(),
            event.watcherId(),
            event.contentId(),
            event.occurredAt()
        )
    );
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleLeft(WatchingSessionLeftEvent event) {
    messagingTemplate.convertAndSend(
        topic(event),
        new WatchingSessionEventMessage(
            "LEFT",
            event.sessionId(),
            event.watcherId(),
            event.contentId(),
            event.occurredAt()
        )
    );
  }

  private String topic(WatchingSessionEnteredEvent event) {
    return WATCHING_SESSION_TOPIC.formatted(event.contentId());
  }

  private String topic(WatchingSessionLeftEvent event) {
    return WATCHING_SESSION_TOPIC.formatted(event.contentId());
  }
}
