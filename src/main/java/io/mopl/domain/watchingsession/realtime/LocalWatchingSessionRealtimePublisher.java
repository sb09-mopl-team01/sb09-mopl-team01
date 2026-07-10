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

  private static final String WATCHING_SESSION_TOPIC = "/sub/contents/%s/watch";

  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public void publish(WatchingSessionChange change) {
    messagingTemplate.convertAndSend(
        WATCHING_SESSION_TOPIC.formatted(change.watchingSession().content().id()),
        change
    );
  }
}
