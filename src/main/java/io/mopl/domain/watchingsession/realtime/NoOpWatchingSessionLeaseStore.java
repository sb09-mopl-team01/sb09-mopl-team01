package io.mopl.domain.watchingsession.realtime;

import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscription;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpWatchingSessionLeaseStore implements WatchingSessionLeaseStore {

  @Override
  public boolean acquire(WatchingSessionSubscription subscription, String nodeId) {
    return true;
  }

  @Override
  public boolean release(WatchingSessionSubscription subscription, String nodeId) {
    return true;
  }

  @Override
  public boolean refresh(WatchingSessionSubscription subscription, String nodeId) {
    return true;
  }

  @Override
  public List<WatchingSessionSubscription> expireStaleLeases() {
    return List.of();
  }
}
