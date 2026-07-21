package io.mopl.domain.content.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.service.ContentExternalSyncService;
import java.time.Instant;
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
class ContentExternalSyncTaskletTest {

  @Mock
  private ContentExternalSyncService contentExternalSyncService;

  @Mock
  private JobExplorer jobExplorer;

  @Mock
  private ContentExternalSyncMetrics contentExternalSyncMetrics;

  @Test
  void execute_runsSyncServiceAndStoresResult() throws Exception {
    Instant syncedAt = Instant.parse("2026-07-06T00:00:00Z");
    given(contentExternalSyncService.syncExternalContents())
        .willReturn(new ExternalContentSyncResult(8, 6, 1, 2, 3, 2, syncedAt));
    ExecutionContext executionContext = new ExecutionContext();
    ChunkContext chunkContext = chunkContext(executionContext);
    ContentExternalSyncTasklet tasklet = tasklet();

    RepeatStatus status = tasklet.execute(null, chunkContext);

    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.FETCHED_COUNT_KEY)).isEqualTo(8);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.ACCEPTED_COUNT_KEY)).isEqualTo(6);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.FILTERED_COUNT_KEY)).isEqualTo(1);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.CREATED_COUNT_KEY)).isEqualTo(2);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.SKIPPED_COUNT_KEY)).isEqualTo(3);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.FAILED_COUNT_KEY)).isEqualTo(2);
    String storedSyncedAt = executionContext.getString(ContentExternalSyncTasklet.SYNCED_AT_KEY);
    assertThat(storedSyncedAt).isEqualTo("2026-07-06T00:00:00Z");
    assertThat(Instant.parse(storedSyncedAt)).isEqualTo(syncedAt);
    verify(contentExternalSyncService).syncExternalContents();
    verify(contentExternalSyncMetrics).record(
        new ExternalContentSyncResult(8, 6, 1, 2, 3, 2, syncedAt)
    );
  }

  @Test
  void execute_doesNotStoreSyncedAtWhenSyncedAtIsNull() throws Exception {
    given(contentExternalSyncService.syncExternalContents())
        .willReturn(new ExternalContentSyncResult(8, 6, 1, 2, 3, 2, null));
    ExecutionContext executionContext = new ExecutionContext();
    ChunkContext chunkContext = chunkContext(executionContext);
    ContentExternalSyncTasklet tasklet = tasklet();

    RepeatStatus status = tasklet.execute(null, chunkContext);

    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.FETCHED_COUNT_KEY)).isEqualTo(8);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.ACCEPTED_COUNT_KEY)).isEqualTo(6);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.FILTERED_COUNT_KEY)).isEqualTo(1);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.CREATED_COUNT_KEY)).isEqualTo(2);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.SKIPPED_COUNT_KEY)).isEqualTo(3);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.FAILED_COUNT_KEY)).isEqualTo(2);
    assertThat(executionContext.containsKey(ContentExternalSyncTasklet.SYNCED_AT_KEY)).isFalse();
    verify(contentExternalSyncMetrics).record(
        new ExternalContentSyncResult(8, 6, 1, 2, 3, 2, null)
    );
  }

  @Test
  void execute_propagatesSyncFailure() {
    RuntimeException failure = new RuntimeException("external sync failed");
    given(contentExternalSyncService.syncExternalContents()).willThrow(failure);
    ContentExternalSyncTasklet tasklet = tasklet();

    assertThatThrownBy(() -> tasklet.execute(null, chunkContext(new ExecutionContext())))
        .isSameAs(failure);
    verify(contentExternalSyncMetrics, never()).record(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_rejectsWhenOlderExecutionIsStillRunning() {
    JobExecution olderExecution = new JobExecution(1L);
    given(jobExplorer.findRunningJobExecutions(ContentExternalSyncJobConfig.JOB_NAME))
        .willReturn(Set.of(olderExecution));
    ContentExternalSyncTasklet tasklet = tasklet();

    assertThatThrownBy(() -> tasklet.execute(null, chunkContext(new ExecutionContext(), 2L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("콘텐츠 외부 동기화 Job이 이미 실행 중입니다.");

    verify(contentExternalSyncService, never()).syncExternalContents();
    verify(contentExternalSyncMetrics, never()).record(org.mockito.ArgumentMatchers.any());
  }

  private ContentExternalSyncTasklet tasklet() {
    return new ContentExternalSyncTasklet(
        contentExternalSyncService,
        jobExplorer,
        contentExternalSyncMetrics
    );
  }

  private ChunkContext chunkContext(ExecutionContext executionContext) {
    return chunkContext(executionContext, 1L);
  }

  private ChunkContext chunkContext(ExecutionContext executionContext, Long jobExecutionId) {
    JobExecution jobExecution = new JobExecution(jobExecutionId);
    jobExecution.setExecutionContext(executionContext);
    StepExecution stepExecution = new StepExecution(ContentExternalSyncJobConfig.STEP_NAME, jobExecution);
    return new ChunkContext(new StepContext(stepExecution));
  }
}
