package io.mopl.domain.content.cache;

import io.mopl.domain.content.cache.dto.ContentBaseCache;
import io.mopl.domain.content.cache.dto.ContentStatsCache;

public record ContentCacheSnapshot(
    ContentBaseCache base,
    ContentStatsCache stats
) {

  public static ContentCacheSnapshot empty() {
    return new ContentCacheSnapshot(null, null);
  }

  public boolean isComplete() {
    return base != null && stats != null;
  }
}
