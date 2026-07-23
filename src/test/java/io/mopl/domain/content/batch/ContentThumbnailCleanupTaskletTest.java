package io.mopl.domain.content.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ContentThumbnailCleanupResult;
import io.mopl.domain.content.service.ContentThumbnailCleanupService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class ContentThumbnailCleanupTaskletTest {

  private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");
  private static final Instant CUTOFF = Instant.parse("2026-04-23T00:00:00Z");

  @Mock
  private ContentThumbnailCleanupService cleanupService;

  @Mock
  private JobExplorer jobExplorer;

  @Test
  void executeStoresCleanupResult() {
    ContentThumbnailCleanupResult cleanupResult = new ContentThumbnailCleanupResult(4, 3, 1, 0);
    given(cleanupService.cleanupExpiredThumbnails(CUTOFF, 100)).willReturn(cleanupResult);
    ExecutionContext executionContext = new ExecutionContext();

    RepeatStatus result = tasklet(90, 100).execute(null, chunkContext(executionContext, 2L));

    assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    assertThat(executionContext.getInt(ContentThumbnailCleanupTasklet.DISCOVERED_COUNT_KEY))
        .isEqualTo(4);
    assertThat(executionContext.getInt(ContentThumbnailCleanupTasklet.CLEANED_COUNT_KEY))
        .isEqualTo(3);
    assertThat(executionContext.getInt(ContentThumbnailCleanupTasklet.SKIPPED_COUNT_KEY))
        .isEqualTo(1);
    assertThat(executionContext.getInt(ContentThumbnailCleanupTasklet.FAILED_COUNT_KEY))
        .isZero();
    assertThat(executionContext.getString(ContentThumbnailCleanupTasklet.CUTOFF_KEY))
        .isEqualTo(CUTOFF.toString());
  }

  @Test
  void executeFailsJobAfterRecordingPartialFailures() {
    ContentThumbnailCleanupResult cleanupResult = new ContentThumbnailCleanupResult(2, 1, 0, 1);
    given(cleanupService.cleanupExpiredThumbnails(CUTOFF, 100)).willReturn(cleanupResult);
    ExecutionContext executionContext = new ExecutionContext();

    assertThatThrownBy(
        () -> tasklet(90, 100).execute(null, chunkContext(executionContext, 2L))
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("콘텐츠 썸네일 정리 중 실패한 항목이 있습니다.");

    assertThat(executionContext.getInt(ContentThumbnailCleanupTasklet.CLEANED_COUNT_KEY))
        .isEqualTo(1);
    assertThat(executionContext.getInt(ContentThumbnailCleanupTasklet.FAILED_COUNT_KEY))
        .isEqualTo(1);
  }

  @Test
  void executeRejectsWhenOlderExecutionIsStillRunning() {
    JobExecution olderExecution = new JobExecution(1L);
    given(jobExplorer.findRunningJobExecutions(ContentThumbnailCleanupJobConfig.JOB_NAME))
        .willReturn(Set.of(olderExecution));

    assertThatThrownBy(
        () -> tasklet(90, 100).execute(null, chunkContext(new ExecutionContext(), 2L))
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("콘텐츠 썸네일 정리 Job이 이미 실행 중입니다.");

    verify(cleanupService, never()).cleanupExpiredThumbnails(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyInt()
    );
  }

  @Test
  void constructorRejectsInvalidPolicyValues() {
    assertThatThrownBy(() -> tasklet(0, 100)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> tasklet(90, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> tasklet(90, 1001)).isInstanceOf(IllegalArgumentException.class);
  }

  private ContentThumbnailCleanupTasklet tasklet(int retentionDays, int chunkSize) {
    return new ContentThumbnailCleanupTasklet(
        cleanupService,
        jobExplorer,
        Clock.fixed(NOW, ZoneOffset.UTC),
        retentionDays,
        chunkSize
    );
  }

  private ChunkContext chunkContext(ExecutionContext executionContext, Long jobExecutionId) {
    JobExecution jobExecution = new JobExecution(jobExecutionId);
    jobExecution.setExecutionContext(executionContext);
    StepExecution stepExecution = new StepExecution(
        ContentThumbnailCleanupJobConfig.STEP_NAME,
        jobExecution
    );
    return new ChunkContext(new StepContext(stepExecution));
  }
}
