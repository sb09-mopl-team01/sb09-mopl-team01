package io.mopl.domain.content.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ContentCacheMetricsTest {

  @Test
  void recordsOnlyFixedCacheOperationResultTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ContentCacheMetrics metrics = new ContentCacheMetrics(registry);

    metrics.record("contentBase", "mget", "hit", 2);

    assertThat(registry.get(ContentCacheMetrics.METRIC_NAME)
        .tags("cacheName", "contentBase", "operation", "mget", "result", "hit")
        .counter()
        .count()).isEqualTo(2.0);
  }
}
