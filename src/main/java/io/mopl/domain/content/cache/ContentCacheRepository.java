package io.mopl.domain.content.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.content.cache.dto.ContentBaseCache;
import io.mopl.domain.content.cache.dto.ContentStatsCache;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ContentCacheRepository {

  static final String BASE_CACHE_NAME = "contentBase";
  static final String STATS_CACHE_NAME = "contentStats";
  private static final String BASE_KEY_PREFIX = "mopl:content:base:";
  private static final String STATS_KEY_PREFIX = "mopl:content:stats:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final ContentCacheProperties properties;
  private final ContentCacheMetrics metrics;

  public ContentCacheSnapshot find(UUID contentId) {
    return findAll(List.of(contentId)).getOrDefault(contentId, ContentCacheSnapshot.empty());
  }

  public Map<UUID, ContentCacheSnapshot> findAll(Collection<UUID> contentIds) {
    List<UUID> uniqueIds = uniqueIds(contentIds);
    if (uniqueIds.isEmpty()) {
      return Map.of();
    }

    List<String> keys = new ArrayList<>(uniqueIds.size() * 2);
    for (UUID contentId : uniqueIds) {
      keys.add(baseKey(contentId));
      keys.add(statsKey(contentId));
    }

    List<String> values;
    try {
      values = redisTemplate.opsForValue().multiGet(keys);
    } catch (RuntimeException e) {
      recordFallback(uniqueIds.size());
      log.warn(
          "Content cache read failed. cacheName=content, operation=mget, result=fallback, errorType={}",
          e.getClass().getSimpleName()
      );
      return emptySnapshots(uniqueIds);
    }

    Map<UUID, ContentCacheSnapshot> snapshots = new LinkedHashMap<>();
    long baseHits = 0;
    long baseMisses = 0;
    long statsHits = 0;
    long statsMisses = 0;
    for (int index = 0; index < uniqueIds.size(); index++) {
      UUID contentId = uniqueIds.get(index);
      String baseValue = valueAt(values, index * 2);
      String statsValue = valueAt(values, index * 2 + 1);
      ContentBaseCache base = deserialize(baseValue, ContentBaseCache.class, BASE_CACHE_NAME, contentId);
      ContentStatsCache stats = deserialize(statsValue, ContentStatsCache.class, STATS_CACHE_NAME, contentId);

      if (base == null) {
        baseMisses++;
      } else {
        baseHits++;
      }
      if (stats == null) {
        statsMisses++;
      } else {
        statsHits++;
      }
      snapshots.put(contentId, new ContentCacheSnapshot(base, stats));
    }

    metrics.record(BASE_CACHE_NAME, "mget", "hit", baseHits);
    metrics.record(BASE_CACHE_NAME, "mget", "miss", baseMisses);
    metrics.record(STATS_CACHE_NAME, "mget", "hit", statsHits);
    metrics.record(STATS_CACHE_NAME, "mget", "miss", statsMisses);
    return snapshots;
  }

  public void putBases(Collection<ContentBaseCache> values) {
    Map<String, Object> entries = new LinkedHashMap<>();
    if (values != null) {
      for (ContentBaseCache value : values) {
        if (value != null && value.id() != null) {
          entries.put(baseKey(value.id()), value);
        }
      }
    }
    writeAll(BASE_CACHE_NAME, entries, properties.baseTtl());
  }

  public void putStats(Collection<ContentStatsCache> values) {
    Map<String, Object> entries = new LinkedHashMap<>();
    if (values != null) {
      for (ContentStatsCache value : values) {
        if (value != null && value.contentId() != null) {
          entries.put(statsKey(value.contentId()), value);
        }
      }
    }
    writeAll(STATS_CACHE_NAME, entries, properties.statsTtl());
  }

  public void evictAll(UUID contentId) {
    evictAll(List.of(contentId));
  }

  public void evictAll(Collection<UUID> contentIds) {
    List<UUID> uniqueIds = uniqueIds(contentIds);
    if (uniqueIds.isEmpty()) {
      return;
    }
    List<String> keys = new ArrayList<>(uniqueIds.size() * 2);
    for (UUID contentId : uniqueIds) {
      keys.add(baseKey(contentId));
      keys.add(statsKey(contentId));
    }
    try {
      redisTemplate.delete(keys);
      metrics.record(BASE_CACHE_NAME, "bulkEvict", "success", uniqueIds.size());
      metrics.record(STATS_CACHE_NAME, "bulkEvict", "success", uniqueIds.size());
      if (uniqueIds.size() > 1) {
        log.info("Content cache evict completed. cacheName=content, operation=bulkEvict, result=success, count={}",
            uniqueIds.size());
      }
    } catch (RuntimeException e) {
      metrics.record(BASE_CACHE_NAME, "bulkEvict", "failure", uniqueIds.size());
      metrics.record(STATS_CACHE_NAME, "bulkEvict", "failure", uniqueIds.size());
      log.warn(
          "Content cache evict failed. cacheName=content, operation=bulkEvict, result=ttlFallback, count={}, errorType={}",
          uniqueIds.size(),
          e.getClass().getSimpleName()
      );
    }
  }

  public void evictStats(UUID contentId) {
    if (contentId != null) {
      deleteKeys(STATS_CACHE_NAME, List.of(statsKey(contentId)));
    }
  }

  private void writeAll(String cacheName, Map<String, Object> entries, Duration ttl) {
    if (entries.isEmpty()) {
      return;
    }

    List<SerializedEntry> serializedEntries = new ArrayList<>(entries.size());
    for (Map.Entry<String, Object> entry : entries.entrySet()) {
      try {
        serializedEntries.add(new SerializedEntry(entry.getKey(), objectMapper.writeValueAsString(entry.getValue())));
      } catch (JsonProcessingException e) {
        metrics.record(cacheName, "pipelineSet", "failure", 1);
        log.warn(
            "Content cache write failed. cacheName={}, operation=pipelineSet, result=skipped, errorType={}",
            cacheName,
            e.getClass().getSimpleName()
        );
      }
    }
    if (serializedEntries.isEmpty()) {
      return;
    }

    StringRedisSerializer serializer = new StringRedisSerializer();
    try {
      redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        for (SerializedEntry entry : serializedEntries) {
          connection.stringCommands().set(
              serializer.serialize(entry.key()),
              serializer.serialize(entry.value()),
              Expiration.milliseconds(ttl.toMillis()),
              SetOption.UPSERT
          );
        }
        return null;
      });
      metrics.record(cacheName, "pipelineSet", "success", serializedEntries.size());
    } catch (RuntimeException e) {
      metrics.record(cacheName, "pipelineSet", "failure", serializedEntries.size());
      log.warn(
          "Content cache write failed. cacheName={}, operation=pipelineSet, result=skipped, errorType={}",
          cacheName,
          e.getClass().getSimpleName()
      );
    }
  }

  private void deleteKeys(String cacheName, Collection<String> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    try {
      redisTemplate.delete(keys);
      metrics.record(cacheName, "bulkEvict", "success", keys.size());
    } catch (RuntimeException e) {
      metrics.record(cacheName, "bulkEvict", "failure", keys.size());
      log.warn(
          "Content cache evict failed. cacheName={}, operation=bulkEvict, result=ttlFallback, count={}, errorType={}",
          cacheName,
          keys.size(),
          e.getClass().getSimpleName()
      );
    }
  }

  private <T> T deserialize(String value, Class<T> type, String cacheName, UUID expectedId) {
    if (value == null) {
      return null;
    }
    try {
      T deserialized = objectMapper.readValue(value, type);
      if (!hasExpectedId(deserialized, expectedId)) {
        throw new IllegalArgumentException("캐시 콘텐츠 ID가 키와 일치하지 않습니다.");
      }
      return deserialized;
    } catch (RuntimeException | JsonProcessingException e) {
      metrics.record(cacheName, "deserialize", "failure", 1);
      log.warn(
          "Content cache read failed. cacheName={}, operation=deserialize, result=miss, errorType={}",
          cacheName,
          e.getClass().getSimpleName()
      );
      return null;
    }
  }

  private boolean hasExpectedId(Object value, UUID expectedId) {
    if (value instanceof ContentBaseCache base) {
      return expectedId.equals(base.id());
    }
    if (value instanceof ContentStatsCache stats) {
      return expectedId.equals(stats.contentId());
    }
    return false;
  }

  private void recordFallback(int count) {
    metrics.record(BASE_CACHE_NAME, "mget", "fallback", count);
    metrics.record(STATS_CACHE_NAME, "mget", "fallback", count);
  }

  private Map<UUID, ContentCacheSnapshot> emptySnapshots(Collection<UUID> contentIds) {
    Map<UUID, ContentCacheSnapshot> snapshots = new LinkedHashMap<>();
    for (UUID contentId : contentIds) {
      snapshots.put(contentId, ContentCacheSnapshot.empty());
    }
    return snapshots;
  }

  private List<UUID> uniqueIds(Collection<UUID> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>();
    for (UUID contentId : contentIds) {
      if (contentId != null) {
        uniqueIds.add(contentId);
      }
    }
    return List.copyOf(uniqueIds);
  }

  private String valueAt(List<String> values, int index) {
    return values != null && index < values.size() ? values.get(index) : null;
  }

  private String baseKey(UUID contentId) {
    return BASE_KEY_PREFIX + contentId;
  }

  private String statsKey(UUID contentId) {
    return STATS_KEY_PREFIX + contentId;
  }

  private record SerializedEntry(String key, String value) {
  }
}
