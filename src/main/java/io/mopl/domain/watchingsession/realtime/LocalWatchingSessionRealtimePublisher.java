package io.mopl.domain.watchingsession.realtime;

import io.mopl.domain.watchingsession.dto.WatchingSessionChange;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalWatchingSessionRealtimePublisher implements WatchingSessionRealtimePublisher {

  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public void publish(WatchingSessionChange change) {
    messagingTemplate.convertAndSend(
        WatchingSessionTopic.of(change.watchingSession().content().id()),
        change
    );
  }
}
