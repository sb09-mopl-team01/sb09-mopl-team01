package io.mopl.domain.watchingsession.event;

import io.mopl.domain.watchingsession.dto.WatchingSessionChange;
import io.mopl.domain.watchingsession.dto.WatchingSessionChangeType;
import lombok.RequiredArgsConstructor;
import io.mopl.domain.watchingsession.realtime.WatchingSessionPresenceStore;
import io.mopl.domain.watchingsession.realtime.WatchingSessionRealtimePublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WatchingSessionWebSocketEventHandler {

  private final WatchingSessionPresenceStore presenceStore;
  private final WatchingSessionRealtimePublisher realtimePublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleEntered(WatchingSessionEnteredEvent event) {
    presenceStore.enter(event.watchingSession().watcher().userId(), event.watchingSession().content().id());
    realtimePublisher.publish(new WatchingSessionChange(
        WatchingSessionChangeType.JOIN,
        event.watchingSession(),
        event.watcherCount()
    ));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleLeft(WatchingSessionLeftEvent event) {
    presenceStore.leave(event.watchingSession().watcher().userId(), event.watchingSession().content().id());
    realtimePublisher.publish(new WatchingSessionChange(
        WatchingSessionChangeType.LEAVE,
        event.watchingSession(),
        event.watcherCount()
    ));
  }
}
