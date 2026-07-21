package io.mopl.domain.content.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.content.cache.dto.ContentBaseCache;
import io.mopl.domain.content.cache.dto.ContentStatsCache;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.types.Expiration;

@ExtendWith(MockitoExtension.class)
class ContentCacheRepositoryTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Mock
  private ContentCacheMetrics metrics;

  private ObjectMapper objectMapper;
  private ContentCacheRepository cacheRepository;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();
    cacheRepository = new ContentCacheRepository(
        redisTemplate,
        objectMapper,
        new ContentCacheProperties(Duration.ofMinutes(20), Duration.ofMinutes(3)),
        metrics
    );
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  void findAllReturnsSeparatedBaseAndStatsCaches() throws Exception {
    UUID contentId = UUID.randomUUID();
    ContentBaseCache base = new ContentBaseCache(
        contentId,
        "title",
        "description",
        ContentType.MOVIE,
        ContentSource.MANUAL,
        "thumbnail",
        Set.of("tag")
    );
    ContentStatsCache stats = new ContentStatsCache(contentId, 4.5, 3);
    given(valueOperations.multiGet(anyList())).willReturn(List.of(
        objectMapper.writeValueAsString(base),
        objectMapper.writeValueAsString(stats)
    ));

    Map<UUID, ContentCacheSnapshot> result = cacheRepository.findAll(List.of(contentId));

    assertThat(result.get(contentId).base()).isEqualTo(base);
    assertThat(result.get(contentId).stats()).isEqualTo(stats);
    verify(valueOperations).multiGet(List.of(
        "mopl:content:base:" + contentId,
        "mopl:content:stats:" + contentId
    ));
    verify(metrics).record(ContentCacheRepository.BASE_CACHE_NAME, "mget", "hit", 1);
    verify(metrics).record(ContentCacheRepository.STATS_CACHE_NAME, "mget", "hit", 1);
  }

  @Test
  void findAllFallsBackToMissWhenRedisReadFails() {
    UUID contentId = UUID.randomUUID();
    given(valueOperations.multiGet(anyList())).willThrow(new RuntimeException("redis unavailable"));

    Map<UUID, ContentCacheSnapshot> result = cacheRepository.findAll(List.of(contentId));

    assertThat(result.get(contentId).isComplete()).isFalse();
    verify(metrics).record(ContentCacheRepository.BASE_CACHE_NAME, "mget", "fallback", 1);
    verify(metrics).record(ContentCacheRepository.STATS_CACHE_NAME, "mget", "fallback", 1);
  }

  @Test
  void invalidCachedContentIdIsHandledAsMiss() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID anotherId = UUID.randomUUID();
    ContentBaseCache invalidBase = new ContentBaseCache(
        anotherId,
        "title",
        "description",
        ContentType.MOVIE,
        ContentSource.MANUAL,
        null,
        Set.of("tag")
    );
    given(valueOperations.multiGet(anyList())).willReturn(java.util.Arrays.asList(
        objectMapper.writeValueAsString(invalidBase),
        null
    ));

    ContentCacheSnapshot result = cacheRepository.find(contentId);

    assertThat(result.isComplete()).isFalse();
    verify(metrics).record(ContentCacheRepository.BASE_CACHE_NAME, "deserialize", "failure", 1);
  }

  @Test
  void putBaseUsesPipelineWithConfiguredTtl() {
    RedisConnection connection = org.mockito.Mockito.mock(RedisConnection.class);
    RedisStringCommands stringCommands = org.mockito.Mockito.mock(RedisStringCommands.class);
    given(connection.stringCommands()).willReturn(stringCommands);
    given(redisTemplate.executePipelined(any(RedisCallback.class))).willAnswer(invocation -> {
      RedisCallback<?> callback = invocation.getArgument(0);
      callback.doInRedis(connection);
      return List.of();
    });
    ContentBaseCache base = new ContentBaseCache(
        UUID.randomUUID(),
        "title",
        "description",
        ContentType.MOVIE,
        ContentSource.MANUAL,
        null,
        Set.of("tag")
    );

    cacheRepository.putBases(List.of(base));

    ArgumentCaptor<Expiration> expirationCaptor = ArgumentCaptor.forClass(Expiration.class);
    verify(stringCommands).set(
        any(byte[].class),
        any(byte[].class),
        expirationCaptor.capture(),
        eq(SetOption.UPSERT)
    );
    assertThat(expirationCaptor.getValue().getExpirationTimeInMilliseconds())
        .isEqualTo(Duration.ofMinutes(20).toMillis());
    verify(metrics).record(ContentCacheRepository.BASE_CACHE_NAME, "pipelineSet", "success", 1);
  }

  @Test
  void putStatsUsesShorterConfiguredTtl() {
    RedisConnection connection = org.mockito.Mockito.mock(RedisConnection.class);
    RedisStringCommands stringCommands = org.mockito.Mockito.mock(RedisStringCommands.class);
    given(connection.stringCommands()).willReturn(stringCommands);
    given(redisTemplate.executePipelined(any(RedisCallback.class))).willAnswer(invocation -> {
      RedisCallback<?> callback = invocation.getArgument(0);
      callback.doInRedis(connection);
      return List.of();
    });
    ContentStatsCache stats = new ContentStatsCache(UUID.randomUUID(), 4.0, 2);

    cacheRepository.putStats(List.of(stats));

    ArgumentCaptor<Expiration> expirationCaptor = ArgumentCaptor.forClass(Expiration.class);
    verify(stringCommands).set(
        any(byte[].class),
        any(byte[].class),
        expirationCaptor.capture(),
        eq(SetOption.UPSERT)
    );
    assertThat(expirationCaptor.getValue().getExpirationTimeInMilliseconds())
        .isEqualTo(Duration.ofMinutes(3).toMillis());
    verify(metrics).record(ContentCacheRepository.STATS_CACHE_NAME, "pipelineSet", "success", 1);
  }

  @Test
  void evictionFailureDoesNotPropagateToApiFlow() {
    UUID contentId = UUID.randomUUID();
    given(redisTemplate.delete(org.mockito.ArgumentMatchers.<String>anyCollection()))
        .willThrow(new RuntimeException("redis unavailable"));

    assertThatCode(() -> cacheRepository.evictAll(contentId)).doesNotThrowAnyException();

    verify(metrics).record(ContentCacheRepository.BASE_CACHE_NAME, "bulkEvict", "failure", 1);
    verify(metrics).record(ContentCacheRepository.STATS_CACHE_NAME, "bulkEvict", "failure", 1);
  }
}
