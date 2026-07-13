package io.mopl.domain.watchingsession.realtime;

import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscription;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "true")
public class RedisWatchingSessionLeaseStore implements WatchingSessionLeaseStore {

  private static final String LEASE_KEY_PREFIX = "watching-session:lease:watcher:";
  private static final String CONTENT_KEY_SEPARATOR = ":content:";

  private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = script(
      "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1]); "
          + "local active = redis.call('ZCARD', KEYS[1]); "
          + "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[4]); "
          + "redis.call('EXPIRE', KEYS[1], ARGV[3]); "
          + "if active == 0 then return 1 end; return 0;"
  );
  private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script(
      "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1]); "
          + "local removed = redis.call('ZREM', KEYS[1], ARGV[2]); "
          + "local active = redis.call('ZCARD', KEYS[1]); "
          + "if active == 0 then redis.call('DEL', KEYS[1]); end; "
          + "if removed == 1 and active == 0 then return 1 end; return 0;"
  );
  private static final DefaultRedisScript<Long> REFRESH_SCRIPT = script(
      "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1]); "
          + "if redis.call('ZSCORE', KEYS[1], ARGV[3]) == false then return 0 end; "
          + "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3]); "
          + "redis.call('EXPIRE', KEYS[1], ARGV[4]); return 1;"
  );
  private static final DefaultRedisScript<Long> EXPIRE_STALE_SCRIPT = script(
      "local removed = redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1]); "
          + "local active = redis.call('ZCARD', KEYS[1]); "
          + "if removed > 0 and active == 0 then redis.call('DEL', KEYS[1]); return 1 end; "
          + "return 0;"
  );

  private final StringRedisTemplate redisTemplate;
  private final Duration leaseTtl;

  public RedisWatchingSessionLeaseStore(
      StringRedisTemplate redisTemplate,
      @Value("${mopl.watching-session.redis.lease-ttl:PT90S}") Duration leaseTtl
  ) {
    this.redisTemplate = redisTemplate;
    this.leaseTtl = leaseTtl;
  }

  @Override
  public boolean acquire(WatchingSessionSubscription subscription, String nodeId) {
    Long result = redisTemplate.execute(
        ACQUIRE_SCRIPT,
        List.of(key(subscription)),
        Long.toString(System.currentTimeMillis()),
        Long.toString(expiresAtMillis()),
        Long.toString(ttlSeconds()),
        nodeId
    );
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public boolean release(WatchingSessionSubscription subscription, String nodeId) {
    Long result = redisTemplate.execute(
        RELEASE_SCRIPT,
        List.of(key(subscription)),
        Long.toString(System.currentTimeMillis()),
        nodeId
    );
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public boolean refresh(WatchingSessionSubscription subscription, String nodeId) {
    Long result = redisTemplate.execute(
        REFRESH_SCRIPT,
        List.of(key(subscription)),
        Long.toString(System.currentTimeMillis()),
        Long.toString(expiresAtMillis()),
        nodeId,
        Long.toString(ttlSeconds())
    );
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public List<WatchingSessionSubscription> expireStaleLeases() {
    List<WatchingSessionSubscription> expiredLastLeases = new ArrayList<>();
    ScanOptions options = ScanOptions.scanOptions()
        .match(LEASE_KEY_PREFIX + "*" + CONTENT_KEY_SEPARATOR + "*")
        .count(100)
        .build();

    try (Cursor<String> cursor = redisTemplate.scan(options)) {
      while (cursor.hasNext()) {
        String key = cursor.next();
        Long result = redisTemplate.execute(
            EXPIRE_STALE_SCRIPT,
            List.of(key),
            Long.toString(System.currentTimeMillis())
        );
        if (Long.valueOf(1L).equals(result)) {
          parseSubscription(key).ifPresent(expiredLastLeases::add);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to clean up expired watching session leases.", e);
    }

    return expiredLastLeases;
  }

  private long expiresAtMillis() {
    return System.currentTimeMillis() + leaseTtl.toMillis();
  }

  private long ttlSeconds() {
    return Math.max(1, leaseTtl.toSeconds() * 2);
  }

  private String key(WatchingSessionSubscription subscription) {
    return LEASE_KEY_PREFIX + subscription.watcherId() + CONTENT_KEY_SEPARATOR + subscription.contentId();
  }

  private java.util.Optional<WatchingSessionSubscription> parseSubscription(String key) {
    String prefix = LEASE_KEY_PREFIX;
    int separatorIndex = key.indexOf(CONTENT_KEY_SEPARATOR);
    if (!key.startsWith(prefix) || separatorIndex < prefix.length()) {
      return java.util.Optional.empty();
    }

    try {
      UUID watcherId = UUID.fromString(key.substring(prefix.length(), separatorIndex));
      UUID contentId = UUID.fromString(key.substring(separatorIndex + CONTENT_KEY_SEPARATOR.length()));
      return java.util.Optional.of(new WatchingSessionSubscription(watcherId, contentId));
    } catch (IllegalArgumentException e) {
      log.warn("Ignoring malformed watching session lease key. key={}", key);
      return java.util.Optional.empty();
    }
  }

  private static DefaultRedisScript<Long> script(String source) {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(source);
    script.setResultType(Long.class);
    return script;
  }
}
