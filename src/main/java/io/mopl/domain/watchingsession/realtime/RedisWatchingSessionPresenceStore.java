package io.mopl.domain.watchingsession.realtime;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "true")
public class RedisWatchingSessionPresenceStore implements WatchingSessionPresenceStore {

  private static final String PRESENCE_KEY_PREFIX = "watching-session:presence:content:";
  private static final DefaultRedisScript<Long> ENTER_SCRIPT = script(
      "redis.call('SADD', KEYS[1], ARGV[1]); "
          + "redis.call('EXPIRE', KEYS[1], ARGV[2]); return 1;"
  );
  private static final DefaultRedisScript<Long> LEAVE_SCRIPT = script(
      "local removed = redis.call('SREM', KEYS[1], ARGV[1]); "
          + "if redis.call('SCARD', KEYS[1]) == 0 then redis.call('DEL', KEYS[1]); end; "
          + "return removed;"
  );

  private final StringRedisTemplate redisTemplate;
  private final Duration presenceTtl;

  public RedisWatchingSessionPresenceStore(
      StringRedisTemplate redisTemplate,
      @Value("${mopl.watching-session.redis.presence-ttl:PT24H}") Duration presenceTtl
  ) {
    this.redisTemplate = redisTemplate;
    this.presenceTtl = presenceTtl;
  }

  @Override
  public void enter(UUID watcherId, UUID contentId) {
    String key = key(contentId);
    redisTemplate.execute(
        ENTER_SCRIPT,
        List.of(key),
        watcherId.toString(),
        Long.toString(Math.max(1, presenceTtl.toSeconds()))
    );
  }

  @Override
  public void leave(UUID watcherId, UUID contentId) {
    redisTemplate.execute(LEAVE_SCRIPT, List.of(key(contentId)), watcherId.toString());
  }

  private String key(UUID contentId) {
    return PRESENCE_KEY_PREFIX + contentId;
  }

  private static DefaultRedisScript<Long> script(String source) {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(source);
    script.setResultType(Long.class);
    return script;
  }
}
