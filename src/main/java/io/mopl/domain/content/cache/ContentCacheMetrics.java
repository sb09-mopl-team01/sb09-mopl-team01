package io.mopl.domain.content.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentCacheMetrics {

  public static final String METRIC_NAME = "mopl.content.cache.operations";

  private final MeterRegistry meterRegistry;

  public void record(String cacheName, String operation, String result, long count) {
    if (count <= 0) {
      return;
    }
    Counter.builder(METRIC_NAME)
        .description("콘텐츠 캐시 작업 결과 누적 건수")
        .tag("cacheName", cacheName)
        .tag("operation", operation)
        .tag("result", result)
        .register(meterRegistry)
        .increment(count);
  }
}
