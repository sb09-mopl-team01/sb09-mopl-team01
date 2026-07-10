package io.mopl.domain.watchingsession.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisWatchingSessionPresenceStoreTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  private final RedisWatchingSessionPresenceStore presenceStore =
      new RedisWatchingSessionPresenceStore(redisTemplate, Duration.ofHours(6));

  @Test
  void enterStoresWatcherInContentPresenceSetAndRefreshesTtl() {
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String key = "watching-session:presence:content:" + contentId;
    presenceStore.enter(watcherId, contentId);

    verify(redisTemplate).execute(
        org.mockito.ArgumentMatchers.<DefaultRedisScript<Long>>any(),
        eq(List.of(key)),
        eq(watcherId.toString()),
        eq("21600")
    );
  }

  @Test
  void leaveRemovesWatcherFromContentPresenceSet() {
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    presenceStore.leave(watcherId, contentId);

    verify(redisTemplate).execute(
        org.mockito.ArgumentMatchers.<DefaultRedisScript<Long>>any(),
        eq(List.of("watching-session:presence:content:" + contentId)),
        eq(watcherId.toString())
    );
  }
}
