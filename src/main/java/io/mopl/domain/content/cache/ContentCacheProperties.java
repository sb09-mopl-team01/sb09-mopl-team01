package io.mopl.domain.content.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mopl.content.cache")
public record ContentCacheProperties(
    Duration baseTtl,
    Duration statsTtl
) {

  public ContentCacheProperties {
    validateTtl(baseTtl, "base-ttl");
    validateTtl(statsTtl, "stats-ttl");
  }

  private static void validateTtl(Duration ttl, String propertyName) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("콘텐츠 캐시 " + propertyName + "은 0보다 커야 합니다.");
    }
  }
}
