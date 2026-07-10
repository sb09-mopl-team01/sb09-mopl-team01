package io.mopl.domain.watchingsession.realtime;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "true")
public class RedisWatchingSessionPresenceStore implements WatchingSessionPresenceStore {

  private static final String PRESENCE_KEY_PREFIX = "watching-session:presence:content:";
  private static final Duration PRESENCE_TTL = Duration.ofHours(24);

  private final StringRedisTemplate redisTemplate;

  @Override
  public void enter(UUID watcherId, UUID contentId) {
    String key = key(contentId);
    redisTemplate.opsForSet().add(key, watcherId.toString());
    redisTemplate.expire(key, PRESENCE_TTL);
  }

  @Override
  public void leave(UUID watcherId, UUID contentId) {
    redisTemplate.opsForSet().remove(key(contentId), watcherId.toString());
  }

  private String key(UUID contentId) {
    return PRESENCE_KEY_PREFIX + contentId;
  }
}
