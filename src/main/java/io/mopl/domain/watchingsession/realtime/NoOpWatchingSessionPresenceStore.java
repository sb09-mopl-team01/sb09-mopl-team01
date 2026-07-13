package io.mopl.domain.watchingsession.realtime;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpWatchingSessionPresenceStore implements WatchingSessionPresenceStore {

  @Override
  public void enter(UUID watcherId, UUID contentId) {
    // 단일 인스턴스 환경에서는 DB가 시청 세션의 최종 상태를 보관한다.
  }

  @Override
  public void leave(UUID watcherId, UUID contentId) {
    // 단일 인스턴스 환경에서는 DB가 시청 세션의 최종 상태를 보관한다.
  }
}
