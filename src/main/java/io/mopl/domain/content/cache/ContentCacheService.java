package io.mopl.domain.content.cache;

import io.mopl.domain.content.cache.dto.ContentBaseCache;
import io.mopl.domain.content.cache.dto.ContentStatsCache;
import io.mopl.domain.content.entity.Content;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContentCacheService {

  private final ContentCacheRepository cacheRepository;
  private final ContentCacheMapper cacheMapper;

  public ContentCacheSnapshot find(UUID contentId) {
    return cacheRepository.find(contentId);
  }

  public Map<UUID, ContentCacheSnapshot> findAll(Collection<UUID> contentIds) {
    return cacheRepository.findAll(contentIds);
  }

  public ContentCacheSnapshot resolveMissing(Content content, ContentCacheSnapshot cached) {
    ContentCacheSnapshot current = cached == null ? ContentCacheSnapshot.empty() : cached;
    ContentBaseCache base = current.base();
    ContentStatsCache stats = current.stats();
    if (base == null) {
      base = cacheMapper.toBase(content);
      cacheRepository.putBases(List.of(base));
    }
    if (stats == null) {
      stats = cacheMapper.toStats(content);
      cacheRepository.putStats(List.of(stats));
    }
    return new ContentCacheSnapshot(base, stats);
  }

  public Map<UUID, ContentCacheSnapshot> resolveMissing(
      Collection<Content> contents,
      Map<UUID, ContentCacheSnapshot> cachedByContentId
  ) {
    Map<UUID, ContentCacheSnapshot> resolved = new LinkedHashMap<>(cachedByContentId);
    List<ContentBaseCache> missingBases = new ArrayList<>();
    List<ContentStatsCache> missingStats = new ArrayList<>();

    for (Content content : contents) {
      ContentCacheSnapshot cached = resolved.getOrDefault(content.getId(), ContentCacheSnapshot.empty());
      ContentBaseCache base = cached.base();
      ContentStatsCache stats = cached.stats();
      if (base == null) {
        base = cacheMapper.toBase(content);
        missingBases.add(base);
      }
      if (stats == null) {
        stats = cacheMapper.toStats(content);
        missingStats.add(stats);
      }
      resolved.put(content.getId(), new ContentCacheSnapshot(base, stats));
    }

    cacheRepository.putBases(missingBases);
    cacheRepository.putStats(missingStats);
    return resolved;
  }

  public void evictAll(UUID contentId) {
    cacheRepository.evictAll(contentId);
  }

  public void evictAll(Collection<UUID> contentIds) {
    cacheRepository.evictAll(contentIds);
  }

  public void evictStats(UUID contentId) {
    cacheRepository.evictStats(contentId);
  }
}
