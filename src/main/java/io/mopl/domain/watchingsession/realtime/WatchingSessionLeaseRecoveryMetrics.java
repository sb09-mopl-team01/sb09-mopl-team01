package io.mopl.domain.watchingsession.realtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WatchingSessionLeaseRecoveryMetrics {

  public static final String METRIC_NAME = "mopl.watching.session.lease.recovery";

  private final MeterRegistry meterRegistry;

  public void record(String outcome) {
    Counter.builder(METRIC_NAME)
        .description("WatchingSession 만료 lease DB 종료 복구 결과")
        .tag("outcome", outcome)
        .register(meterRegistry)
        .increment();
  }
}
