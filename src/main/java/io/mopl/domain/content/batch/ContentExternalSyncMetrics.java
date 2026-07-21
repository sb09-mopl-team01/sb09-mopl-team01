package io.mopl.domain.content.batch;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.mopl.domain.content.dto.ExternalContentSyncResult;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentExternalSyncMetrics {

  public static final String METRIC_NAME = "mopl.content.external.sync.items";
  static final String JOB_NAME_TAG = "jobName";
  static final String RESULT_TAG = "result";

  static final String RESULT_FETCHED = "fetched";
  static final String RESULT_ACCEPTED = "accepted";
  static final String RESULT_FILTERED = "filtered";
  static final String RESULT_CREATED = "created";
  static final String RESULT_SKIPPED = "skipped";
  static final String RESULT_FAILED = "failed";

  private final MeterRegistry meterRegistry;

  public void record(ExternalContentSyncResult result) {
    Objects.requireNonNull(result, "외부 콘텐츠 동기화 결과는 필수입니다.");

    record(RESULT_FETCHED, result.fetchedCount());
    record(RESULT_ACCEPTED, result.acceptedCount());
    record(RESULT_FILTERED, result.filteredCount());
    record(RESULT_CREATED, result.createdCount());
    record(RESULT_SKIPPED, result.skippedCount());
    record(RESULT_FAILED, result.failedCount());
  }

  private void record(String result, int count) {
    Counter counter = Counter.builder(METRIC_NAME)
        .description("외부 콘텐츠 동기화 처리 결과 누적 건수")
        .tag(JOB_NAME_TAG, ContentExternalSyncJobConfig.JOB_NAME)
        .tag(RESULT_TAG, result)
        .register(meterRegistry);

    if (count > 0) {
      counter.increment(count);
    }
  }
}
