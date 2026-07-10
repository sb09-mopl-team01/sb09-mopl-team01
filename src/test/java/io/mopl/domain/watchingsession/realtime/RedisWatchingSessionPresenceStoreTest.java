package io.mopl.domain.watchingsession.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisWatchingSessionPresenceStoreTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  @SuppressWarnings("unchecked")
  private final SetOperations<String, String> setOperations = mock(SetOperations.class);
  private final RedisWatchingSessionPresenceStore presenceStore =
      new RedisWatchingSessionPresenceStore(redisTemplate);

  @Test
  void enterStoresWatcherInContentPresenceSetAndRefreshesTtl() {
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    String key = "watching-session:presence:content:" + contentId;
    when(redisTemplate.opsForSet()).thenReturn(setOperations);

    presenceStore.enter(watcherId, contentId);

    verify(setOperations).add(key, watcherId.toString());
    verify(redisTemplate).expire(eq(key), eq(Duration.ofHours(24)));
  }

  @Test
  void leaveRemovesWatcherFromContentPresenceSet() {
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    when(redisTemplate.opsForSet()).thenReturn(setOperations);

    presenceStore.leave(watcherId, contentId);

    verify(setOperations).remove("watching-session:presence:content:" + contentId, watcherId.toString());
  }
}
