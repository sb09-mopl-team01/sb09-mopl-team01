package io.mopl.domain.watchingsession.event;

import io.mopl.domain.watchingsession.dto.WatchingSessionChange;
import io.mopl.domain.watchingsession.dto.WatchingSessionChangeType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WatchingSessionWebSocketEventHandler {

  private static final String WATCHING_SESSION_TOPIC = "/sub/contents/%s/watch";

  private final SimpMessagingTemplate messagingTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleEntered(WatchingSessionEnteredEvent event) {
    messagingTemplate.convertAndSend(
        topic(event),
        new WatchingSessionChange(
            WatchingSessionChangeType.JOIN,
            event.watchingSession(),
            event.watcherCount()
        )
    );
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleLeft(WatchingSessionLeftEvent event) {
    messagingTemplate.convertAndSend(
        topic(event),
        new WatchingSessionChange(
            WatchingSessionChangeType.LEAVE,
            event.watchingSession(),
            event.watcherCount()
        )
    );
  }

  private String topic(WatchingSessionEnteredEvent event) {
    return WATCHING_SESSION_TOPIC.formatted(event.watchingSession().content().id());
  }

  private String topic(WatchingSessionLeftEvent event) {
    return WATCHING_SESSION_TOPIC.formatted(event.watchingSession().content().id());
  }
}
