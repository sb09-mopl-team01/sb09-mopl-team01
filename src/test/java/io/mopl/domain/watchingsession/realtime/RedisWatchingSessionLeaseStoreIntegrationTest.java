package io.mopl.domain.watchingsession.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscription;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisWatchingSessionLeaseStoreIntegrationTest {

  private static final int REDIS_PORT = 6379;
  private static final Duration LEASE_TTL = Duration.ofSeconds(5);

  @Container
  private static final GenericContainer<?> REDIS = new GenericContainer<>(
      DockerImageName.parse("redis:7.2-alpine")
  ).withExposedPorts(REDIS_PORT);

  private static LettuceConnectionFactory connectionFactory;

  private StringRedisTemplate redisTemplate;
  private RedisWatchingSessionLeaseStore leaseStore;

  @BeforeAll
  static void setUpConnectionFactory() {
    RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
        REDIS.getHost(),
        REDIS.getMappedPort(REDIS_PORT)
    );
    connectionFactory = new LettuceConnectionFactory(configuration);
    connectionFactory.afterPropertiesSet();
  }

  @AfterAll
  static void tearDownConnectionFactory() {
    connectionFactory.destroy();
  }

  @BeforeEach
  void setUp() {
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    leaseStore = new RedisWatchingSessionLeaseStore(redisTemplate, properties());
  }

  @AfterEach
  void flushRedis() {
    try (RedisConnection connection = connectionFactory.getConnection()) {
      connection.serverCommands().flushDb();
    }
  }

  @Test
  void acquireCalculatesLeaseExpiryFromRedisServerTime() {
    WatchingSessionSubscription subscription = subscription();
    long before = redisTimeMillis();

    assertThat(leaseStore.acquire(subscription, "node-a")).isTrue();

    long after = redisTimeMillis();
    Double expiresAt = redisTemplate.opsForZSet().score(leaseStore.key(subscription), "node-a");
    assertThat(expiresAt).isNotNull();
    assertThat(expiresAt.longValue()).isBetween(
        before + LEASE_TTL.toMillis(),
        after + LEASE_TTL.toMillis()
    );
  }

  @Test
  void expiresOnlyTheLastLeaseAndBlocksAcquireWhileDatabaseCloseIsClaimed() {
    WatchingSessionSubscription subscription = subscription();
    assertThat(leaseStore.acquire(subscription, "node-a")).isTrue();
    assertThat(leaseStore.acquire(subscription, "node-b")).isFalse();
    expireMember(subscription, "node-a");

    assertThat(leaseStore.expireStaleLeases()).isEmpty();

    expireMember(subscription, "node-b");
    assertThat(leaseStore.expireStaleLeases()).containsExactly(subscription);
    assertThat(leaseStore.claimRecovery(subscription, "recovery-a")).isTrue();
    assertThat(leaseStore.acquire(subscription, "node-c")).isFalse();
    assertThat(leaseStore.completeRecovery(subscription, "recovery-a")).isTrue();
    assertThat(redisTemplate.hasKey(leaseStore.key(subscription))).isFalse();
  }

  @Test
  void databaseFailureLeavesCompensationAndFollowingScheduleCleansItAfterSuccess() {
    WatchingSessionSubscription subscription = subscription();
    WatchingSessionService watchingSessionService = mock(WatchingSessionService.class);
    WatchingSessionLeaseRecoveryCoordinator coordinator = new WatchingSessionLeaseRecoveryCoordinator(
        leaseStore,
        new WatchingSessionNodeId("node-a", ""),
        watchingSessionService,
        new WatchingSessionLeaseRecoveryMetrics(new SimpleMeterRegistry())
    );
    doThrow(new IllegalStateException("database unavailable"))
        .doNothing()
        .when(watchingSessionService)
        .endWatchingIfPresent(subscription.watcherId(), subscription.contentId());
    assertThat(leaseStore.acquire(subscription, "node-a")).isTrue();
    assertThat(leaseStore.release(subscription, "node-a")).isTrue();

    coordinator.recover(subscription);

    assertThat(redisTemplate.hasKey(leaseStore.key(subscription))).isTrue();
    assertThat(leaseStore.expireStaleLeases()).containsExactly(subscription);

    coordinator.recover(subscription);

    verify(watchingSessionService, times(2)).endWatchingIfPresent(
        subscription.watcherId(), subscription.contentId());
    assertThat(redisTemplate.hasKey(leaseStore.key(subscription))).isFalse();
  }

  @Test
  void newLeaseCancelsPendingRecoveryBeforeDatabaseCloseClaim() {
    WatchingSessionSubscription subscription = subscription();
    WatchingSessionService watchingSessionService = mock(WatchingSessionService.class);
    WatchingSessionLeaseRecoveryCoordinator coordinator = new WatchingSessionLeaseRecoveryCoordinator(
        leaseStore,
        new WatchingSessionNodeId("node-a", ""),
        watchingSessionService,
        new WatchingSessionLeaseRecoveryMetrics(new SimpleMeterRegistry())
    );
    assertThat(leaseStore.acquire(subscription, "node-a")).isTrue();
    assertThat(leaseStore.release(subscription, "node-a")).isTrue();

    assertThat(leaseStore.acquire(subscription, "node-b")).isTrue();
    coordinator.recover(subscription);

    verify(watchingSessionService, never()).endWatchingIfPresent(
        subscription.watcherId(), subscription.contentId());
    assertThat(redisTemplate.opsForZSet().score(
        leaseStore.key(subscription),
        RedisWatchingSessionLeaseStore.RECOVERY_DUE_MEMBER
    )).isNull();
  }

  @Test
  void retryLimitMovesRecoveryToFailedRetentionWithoutFurtherAutomaticRetry() {
    WatchingSessionSubscription subscription = subscription();
    assertThat(leaseStore.acquire(subscription, "node-a")).isTrue();
    assertThat(leaseStore.release(subscription, "node-a")).isTrue();

    for (int attempt = 1; attempt <= 3; attempt++) {
      String owner = "recovery-" + attempt;
      assertThat(leaseStore.claimRecovery(subscription, owner)).isTrue();
      WatchingSessionLeaseRecoveryFailure failure = leaseStore.recordRecoveryFailure(
          subscription,
          owner
      );
      if (attempt < 3) {
        assertThat(failure.status())
            .isEqualTo(WatchingSessionLeaseRecoveryFailure.Status.RETRY_SCHEDULED);
      } else {
        assertThat(failure.status())
            .isEqualTo(WatchingSessionLeaseRecoveryFailure.Status.EXHAUSTED);
      }
      assertThat(failure.attempt()).isEqualTo(attempt);
    }

    assertThat(leaseStore.expireStaleLeases()).isEmpty();
    assertThat(redisTemplate.opsForZSet().score(
        leaseStore.key(subscription),
        RedisWatchingSessionLeaseStore.RECOVERY_FAILED_MEMBER
    )).isNotNull();
    assertThat(redisTemplate.getExpire(leaseStore.key(subscription)))
        .isPositive()
        .isLessThanOrEqualTo(Duration.ofMinutes(1).toSeconds());
  }

  @Test
  void concurrentTasksProduceOneGlobalFirstAcquireAndOneGlobalLastRelease() throws Exception {
    WatchingSessionSubscription subscription = subscription();
    List<Boolean> acquireResults = runConcurrently(
        () -> leaseStore.acquire(subscription, "node-a"),
        () -> leaseStore.acquire(subscription, "node-b")
    );

    assertThat(acquireResults).containsExactlyInAnyOrder(true, false);

    List<Boolean> releaseResults = runConcurrently(
        () -> leaseStore.release(subscription, "node-a"),
        () -> leaseStore.release(subscription, "node-b")
    );

    assertThat(releaseResults).containsExactlyInAnyOrder(true, false);
    assertThat(leaseStore.expireStaleLeases()).containsExactly(subscription);
  }

  private void expireMember(WatchingSessionSubscription subscription, String nodeId) {
    redisTemplate.opsForZSet().add(
        leaseStore.key(subscription),
        nodeId,
        redisTimeMillis() - 1
    );
  }

  private long redisTimeMillis() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(
        "local time = redis.call('TIME'); "
            + "return time[1] * 1000 + math.floor(time[2] / 1000);"
    );
    script.setResultType(Long.class);
    Long result = redisTemplate.execute(script, List.of());
    if (result == null) {
      throw new IllegalStateException("Redis TIME returned no result");
    }
    return result;
  }

  private List<Boolean> runConcurrently(
      Callable<Boolean> first,
      Callable<Boolean> second
  ) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Boolean> firstResult = executor.submit(awaitStart(ready, start, first));
      Future<Boolean> secondResult = executor.submit(awaitStart(ready, start, second));
      ready.await();
      start.countDown();
      return List.of(firstResult.get(), secondResult.get());
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<Boolean> awaitStart(
      CountDownLatch ready,
      CountDownLatch start,
      Callable<Boolean> operation
  ) {
    return () -> {
      ready.countDown();
      start.await();
      return operation.call();
    };
  }

  private WatchingSessionLeaseProperties properties() {
    return new WatchingSessionLeaseProperties(
        LEASE_TTL,
        Duration.ofHours(1),
        1,
        Duration.ofSeconds(2),
        Duration.ofSeconds(30),
        Duration.ofMinutes(1),
        3,
        100,
        Duration.ZERO,
        2.0,
        Duration.ZERO
    );
  }

  private WatchingSessionSubscription subscription() {
    return new WatchingSessionSubscription(UUID.randomUUID(), UUID.randomUUID());
  }
}
