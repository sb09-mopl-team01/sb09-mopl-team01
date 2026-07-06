package io.mopl.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CacheFallbackConfig implements CachingConfigurer {

  @Override
  public CacheErrorHandler errorHandler() {
    return new MoplCacheErrorHandler();
  }

  private static class MoplCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
      log.warn("Redis GET Error: key={}, msg={}. Attempting to fetch from DB instead.", key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
      log.warn("Redis PUT Error: key={}, msg={}. Skipping cache update.", key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
      log.warn("Redis EVICT Error: key={}, msg={}. Skipping cache eviction.", key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
      log.warn("Redis CLEAR Error: msg={}. Skipping cache clear.", exception.getMessage());
    }
  }
}
