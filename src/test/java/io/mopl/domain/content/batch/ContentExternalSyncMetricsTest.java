package io.mopl.domain.content.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.mopl.domain.content.dto.ExternalContentSyncResult;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentExternalSyncMetricsTest {

  private SimpleMeterRegistry meterRegistry;
  private ContentExternalSyncMetrics metrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    metrics = new ContentExternalSyncMetrics(meterRegistry);
  }

  @Test
  void record_incrementsEachResultCounter() {
    ExternalContentSyncResult result = new ExternalContentSyncResult(
        10,
        8,
        1,
        3,
        4,
        2,
        Instant.parse("2026-07-13T00:00:00Z")
    );

    metrics.record(result);

    Map<String, Double> expectedCounts = Map.of(
        ContentExternalSyncMetrics.RESULT_FETCHED, 10.0,
        ContentExternalSyncMetrics.RESULT_ACCEPTED, 8.0,
        ContentExternalSyncMetrics.RESULT_FILTERED, 1.0,
        ContentExternalSyncMetrics.RESULT_CREATED, 3.0,
        ContentExternalSyncMetrics.RESULT_SKIPPED, 4.0,
        ContentExternalSyncMetrics.RESULT_FAILED, 2.0
    );
    expectedCounts.forEach((resultTag, expectedCount) ->
        assertThat(counter(resultTag).count()).isEqualTo(expectedCount)
    );
  }

  @Test
  void record_accumulatesCountsAcrossExecutions() {
    ExternalContentSyncResult result = new ExternalContentSyncResult(3, 3, 0, 2, 1, 0, null);

    metrics.record(result);
    metrics.record(result);

    assertThat(counter(ContentExternalSyncMetrics.RESULT_FETCHED).count()).isEqualTo(6.0);
    assertThat(counter(ContentExternalSyncMetrics.RESULT_CREATED).count()).isEqualTo(4.0);
    assertThat(counter(ContentExternalSyncMetrics.RESULT_SKIPPED).count()).isEqualTo(2.0);
  }

  @Test
  void record_registersZeroCountSeriesWithoutIncrementing() {
    metrics.record(new ExternalContentSyncResult(0, 0, 0, 0, 0, 0, null));

    assertThat(counter(ContentExternalSyncMetrics.RESULT_FETCHED).count()).isZero();
    assertThat(counter(ContentExternalSyncMetrics.RESULT_FAILED).count()).isZero();
  }

  @Test
  void record_rejectsNullResult() {
    assertThatNullPointerException()
        .isThrownBy(() -> metrics.record(null))
        .withMessage("외부 콘텐츠 동기화 결과는 필수입니다.");
  }

  private Counter counter(String resultTag) {
    return meterRegistry.get(ContentExternalSyncMetrics.METRIC_NAME)
        .tag(ContentExternalSyncMetrics.JOB_NAME_TAG, ContentExternalSyncJobConfig.JOB_NAME)
        .tag(ContentExternalSyncMetrics.RESULT_TAG, resultTag)
        .counter();
  }
}
