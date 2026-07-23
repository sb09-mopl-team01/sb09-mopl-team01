package io.mopl.domain.watchingsession.realtime;

import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
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

  static final String LEASE_KEY_PREFIX = "watching-session:lease:watcher:";
  static final String CONTENT_KEY_SEPARATOR = ":content:";
  static final String RECOVERY_DUE_MEMBER = "__recovery_due__";
  static final String RECOVERY_CREATED_MEMBER = "__recovery_created__";
  static final String RECOVERY_ATTEMPTS_MEMBER = "__recovery_attempts__";
  static final String RECOVERY_FAILED_MEMBER = "__recovery_failed__";
  static final String RECOVERY_LOCK_PREFIX = "__recovery_lock__:";

  private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = script("""
      local redisTime = redis.call('TIME')
      local now = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
      local entries = redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES')
      for index = 1, #entries, 2 do
        local member = entries[index]
        local score = tonumber(entries[index + 1])
        if string.sub(member, 1, 18) == '__recovery_lock__:' then
          if -score > now then
            return -1
          end
          redis.call('ZREM', KEYS[1], member)
        end
      end
      redis.call('ZREMRANGEBYSCORE', KEYS[1], '(0', now)
      local active = redis.call('ZCOUNT', KEYS[1], '(0', '+inf')
      redis.call(
        'ZREM',
        KEYS[1],
        '__recovery_due__',
        '__recovery_created__',
        '__recovery_attempts__',
        '__recovery_failed__'
      )
      redis.call('ZADD', KEYS[1], now + tonumber(ARGV[2]), ARGV[1])
      redis.call('PEXPIRE', KEYS[1], ARGV[3])
      if active == 0 then
        return 1
      end
      return 0
      """);

  private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script("""
      local redisTime = redis.call('TIME')
      local now = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
      local expired = redis.call('ZREMRANGEBYSCORE', KEYS[1], '(0', now)
      local removed = redis.call('ZREM', KEYS[1], ARGV[1])
      local active = redis.call('ZCOUNT', KEYS[1], '(0', '+inf')
      if active == 0 and expired + removed > 0 then
        if redis.call('ZSCORE', KEYS[1], '__recovery_due__') == false
            and redis.call('ZSCORE', KEYS[1], '__recovery_failed__') == false then
          redis.call('ZADD', KEYS[1], -now, '__recovery_due__')
          redis.call('ZADD', KEYS[1], -now, '__recovery_created__')
          redis.call('ZADD', KEYS[1], 0, '__recovery_attempts__')
          redis.call('PEXPIRE', KEYS[1], ARGV[2])
        end
        return 1
      end
      if redis.call('ZCARD', KEYS[1]) == 0 then
        redis.call('DEL', KEYS[1])
      end
      return 0
      """);

  private static final DefaultRedisScript<Long> REFRESH_SCRIPT = script("""
      local redisTime = redis.call('TIME')
      local now = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
      redis.call('ZREMRANGEBYSCORE', KEYS[1], '(0', now)
      if redis.call('ZSCORE', KEYS[1], ARGV[1]) == false then
        return 0
      end
      redis.call('ZADD', KEYS[1], now + tonumber(ARGV[2]), ARGV[1])
      redis.call('PEXPIRE', KEYS[1], ARGV[3])
      return 1
      """);

  private static final DefaultRedisScript<Long> EXPIRE_STALE_SCRIPT = script("""
      local redisTime = redis.call('TIME')
      local now = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
      local removed = redis.call('ZREMRANGEBYSCORE', KEYS[1], '(0', now)
      local entries = redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES')
      local locked = false
      for index = 1, #entries, 2 do
        local member = entries[index]
        local score = tonumber(entries[index + 1])
        if string.sub(member, 1, 18) == '__recovery_lock__:' then
          if -score > now then
            locked = true
          else
            redis.call('ZREM', KEYS[1], member)
          end
        end
      end

      local failedScore = redis.call('ZSCORE', KEYS[1], '__recovery_failed__')
      if failedScore ~= false and -tonumber(failedScore) <= now then
        redis.call('ZREM', KEYS[1], '__recovery_failed__')
        failedScore = false
      end

      local active = redis.call('ZCOUNT', KEYS[1], '(0', '+inf')
      if active > 0 then
        redis.call(
          'ZREM',
          KEYS[1],
          '__recovery_due__',
          '__recovery_created__',
          '__recovery_attempts__',
          '__recovery_failed__'
        )
        return 0
      end

      local dueScore = redis.call('ZSCORE', KEYS[1], '__recovery_due__')
      if removed > 0 and dueScore == false and failedScore == false then
        redis.call('ZADD', KEYS[1], -now, '__recovery_due__')
        redis.call('ZADD', KEYS[1], -now, '__recovery_created__')
        redis.call('ZADD', KEYS[1], 0, '__recovery_attempts__')
        redis.call('PEXPIRE', KEYS[1], ARGV[1])
        dueScore = -now
      end

      if failedScore == false and dueScore ~= false
          and -tonumber(dueScore) <= now and not locked then
        return 1
      end
      if redis.call('ZCARD', KEYS[1]) == 0 then
        redis.call('DEL', KEYS[1])
      end
      return 0
      """);

  private static final DefaultRedisScript<Long> CLAIM_RECOVERY_SCRIPT = script("""
      local redisTime = redis.call('TIME')
      local now = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
      redis.call('ZREMRANGEBYSCORE', KEYS[1], '(0', now)
      local active = redis.call('ZCOUNT', KEYS[1], '(0', '+inf')
      if active > 0 then
        redis.call(
          'ZREM',
          KEYS[1],
          '__recovery_due__',
          '__recovery_created__',
          '__recovery_attempts__',
          '__recovery_failed__'
        )
        return 0
      end
      if redis.call('ZSCORE', KEYS[1], '__recovery_failed__') ~= false then
        return 0
      end
      local dueScore = redis.call('ZSCORE', KEYS[1], '__recovery_due__')
      if dueScore == false or -tonumber(dueScore) > now then
        return 0
      end

      local entries = redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES')
      for index = 1, #entries, 2 do
        local member = entries[index]
        local score = tonumber(entries[index + 1])
        if string.sub(member, 1, 18) == '__recovery_lock__:' then
          if -score > now then
            return 0
          end
          redis.call('ZREM', KEYS[1], member)
        end
      end

      redis.call('ZADD', KEYS[1], -(now + tonumber(ARGV[2])), '__recovery_lock__:' .. ARGV[1])
      return 1
      """);

  private static final DefaultRedisScript<Long> COMPLETE_RECOVERY_SCRIPT = script("""
      local lockMember = '__recovery_lock__:' .. ARGV[1]
      if redis.call('ZSCORE', KEYS[1], lockMember) == false then
        return 0
      end
      redis.call(
        'ZREM',
        KEYS[1],
        lockMember,
        '__recovery_due__',
        '__recovery_created__',
        '__recovery_attempts__',
        '__recovery_failed__'
      )
      if redis.call('ZCOUNT', KEYS[1], '(0', '+inf') == 0 then
        redis.call('DEL', KEYS[1])
      end
      return 1
      """);

  private static final DefaultRedisScript<Long> RECORD_RECOVERY_FAILURE_SCRIPT = script("""
      local redisTime = redis.call('TIME')
      local now = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
      local lockMember = '__recovery_lock__:' .. ARGV[1]
      if redis.call('ZSCORE', KEYS[1], lockMember) == false then
        return 0
      end

      local attemptScore = redis.call('ZSCORE', KEYS[1], '__recovery_attempts__')
      local attempt = 1
      if attemptScore ~= false then
        attempt = math.floor(-tonumber(attemptScore)) + 1
      end
      local createdScore = redis.call('ZSCORE', KEYS[1], '__recovery_created__')
      local createdAt = now
      if createdScore ~= false then
        createdAt = -tonumber(createdScore)
      end
      redis.call('ZREM', KEYS[1], lockMember)

      if attempt >= tonumber(ARGV[2]) or now - createdAt >= tonumber(ARGV[3]) then
        redis.call(
          'ZREM',
          KEYS[1],
          '__recovery_due__',
          '__recovery_created__',
          '__recovery_attempts__'
        )
        redis.call('ZADD', KEYS[1], -(now + tonumber(ARGV[4])), '__recovery_failed__')
        redis.call('PEXPIRE', KEYS[1], ARGV[4])
        return -attempt
      end

      local delay = tonumber(ARGV[5]) * math.pow(tonumber(ARGV[6]), attempt - 1)
      delay = math.min(delay, tonumber(ARGV[7]))
      redis.call('ZADD', KEYS[1], -(now + delay), '__recovery_due__')
      redis.call('ZADD', KEYS[1], -attempt, '__recovery_attempts__')
      return attempt
      """);

  private final StringRedisTemplate redisTemplate;
  private final WatchingSessionLeaseProperties properties;

  public RedisWatchingSessionLeaseStore(
      StringRedisTemplate redisTemplate,
      WatchingSessionLeaseProperties properties
  ) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  @Override
  public boolean acquire(WatchingSessionSubscription subscription, String nodeId) {
    Long result = redisTemplate.execute(
        ACQUIRE_SCRIPT,
        List.of(key(subscription)),
        nodeId,
        Long.toString(properties.leaseTtl().toMillis()),
        Long.toString(properties.leaseKeyRetention().toMillis())
    );
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public boolean release(WatchingSessionSubscription subscription, String nodeId) {
    Long result = redisTemplate.execute(
        RELEASE_SCRIPT,
        List.of(key(subscription)),
        nodeId,
        Long.toString(properties.recoveryRetention().toMillis())
    );
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public boolean refresh(WatchingSessionSubscription subscription, String nodeId) {
    Long result = redisTemplate.execute(
        REFRESH_SCRIPT,
        List.of(key(subscription)),
        nodeId,
        Long.toString(properties.leaseTtl().toMillis()),
        Long.toString(properties.leaseKeyRetention().toMillis())
    );
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public List<WatchingSessionSubscription> expireStaleLeases() {
    List<WatchingSessionSubscription> dueRecoveries = new ArrayList<>();
    ScanOptions options = ScanOptions.scanOptions()
        .match(LEASE_KEY_PREFIX + "*" + CONTENT_KEY_SEPARATOR + "*")
        .count(properties.recoveryBatchSize())
        .build();

    try (Cursor<String> cursor = redisTemplate.scan(options)) {
      while (cursor.hasNext() && dueRecoveries.size() < properties.recoveryBatchSize()) {
        String leaseKey = cursor.next();
        Long result = redisTemplate.execute(
            EXPIRE_STALE_SCRIPT,
            List.of(leaseKey),
            Long.toString(properties.recoveryRetention().toMillis())
        );
        if (Long.valueOf(1L).equals(result)) {
          parseSubscription(leaseKey).ifPresent(dueRecoveries::add);
        }
      }
    } catch (RuntimeException exception) {
      log.warn("Failed to clean up expired watching session leases.", exception);
    }

    return dueRecoveries;
  }

  @Override
  public boolean claimRecovery(
      WatchingSessionSubscription subscription,
      String recoveryOwnerId
  ) {
    Long result = redisTemplate.execute(
        CLAIM_RECOVERY_SCRIPT,
        List.of(key(subscription)),
        recoveryOwnerId,
        Long.toString(properties.recoveryLockTtl().toMillis())
    );
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public boolean completeRecovery(
      WatchingSessionSubscription subscription,
      String recoveryOwnerId
  ) {
    Long result = redisTemplate.execute(
        COMPLETE_RECOVERY_SCRIPT,
        List.of(key(subscription)),
        recoveryOwnerId
    );
    return Long.valueOf(1L).equals(result);
  }

  @Override
  public WatchingSessionLeaseRecoveryFailure recordRecoveryFailure(
      WatchingSessionSubscription subscription,
      String recoveryOwnerId
  ) {
    Long result = redisTemplate.execute(
        RECORD_RECOVERY_FAILURE_SCRIPT,
        List.of(key(subscription)),
        recoveryOwnerId,
        Integer.toString(properties.recoveryMaxAttempts()),
        Long.toString(properties.recoveryRetention().toMillis()),
        Long.toString(properties.recoveryFailedRetention().toMillis()),
        Long.toString(properties.recoveryInitialBackoff().toMillis()),
        Double.toString(properties.recoveryBackoffMultiplier()),
        Long.toString(properties.recoveryMaxBackoff().toMillis())
    );
    if (result == null || result == 0L) {
      return WatchingSessionLeaseRecoveryFailure.notRecorded();
    }
    if (result < 0L) {
      return WatchingSessionLeaseRecoveryFailure.exhausted(Math.toIntExact(-result));
    }
    return WatchingSessionLeaseRecoveryFailure.retryScheduled(Math.toIntExact(result));
  }

  String key(WatchingSessionSubscription subscription) {
    return LEASE_KEY_PREFIX + subscription.watcherId()
        + CONTENT_KEY_SEPARATOR + subscription.contentId();
  }

  private Optional<WatchingSessionSubscription> parseSubscription(String leaseKey) {
    int separatorIndex = leaseKey.indexOf(CONTENT_KEY_SEPARATOR);
    if (!leaseKey.startsWith(LEASE_KEY_PREFIX) || separatorIndex < LEASE_KEY_PREFIX.length()) {
      return Optional.empty();
    }

    try {
      UUID watcherId = UUID.fromString(
          leaseKey.substring(LEASE_KEY_PREFIX.length(), separatorIndex)
      );
      UUID contentId = UUID.fromString(
          leaseKey.substring(separatorIndex + CONTENT_KEY_SEPARATOR.length())
      );
      return Optional.of(new WatchingSessionSubscription(watcherId, contentId));
    } catch (IllegalArgumentException exception) {
      log.warn("Ignoring malformed watching session lease key. key={}", leaseKey);
      return Optional.empty();
    }
  }

  private static DefaultRedisScript<Long> script(String source) {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(source);
    script.setResultType(Long.class);
    return script;
  }
}
