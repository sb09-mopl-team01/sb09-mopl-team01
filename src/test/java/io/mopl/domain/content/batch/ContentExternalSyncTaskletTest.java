package io.mopl.domain.content.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.service.ContentExternalSyncService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class ContentExternalSyncTaskletTest {

  @Mock
  private ContentExternalSyncService contentExternalSyncService;

  @Test
  void execute_runsSyncServiceAndStoresResult() throws Exception {
    Instant syncedAt = Instant.parse("2026-07-06T00:00:00Z");
    given(contentExternalSyncService.syncExternalContents())
        .willReturn(new ExternalContentSyncResult(2, 3, 1, syncedAt));
    ExecutionContext executionContext = new ExecutionContext();
    ChunkContext chunkContext = chunkContext(executionContext);
    ContentExternalSyncTasklet tasklet = new ContentExternalSyncTasklet(contentExternalSyncService);

    RepeatStatus status = tasklet.execute(null, chunkContext);

    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.CREATED_COUNT_KEY)).isEqualTo(2);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.SKIPPED_COUNT_KEY)).isEqualTo(3);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.FAILED_COUNT_KEY)).isEqualTo(1);
    assertThat(executionContext.getString(ContentExternalSyncTasklet.SYNCED_AT_KEY))
        .isEqualTo("2026-07-06T00:00:00Z");
    verify(contentExternalSyncService).syncExternalContents();
  }

  @Test
  void execute_doesNotStoreSyncedAtWhenSyncedAtIsNull() throws Exception {
    given(contentExternalSyncService.syncExternalContents())
        .willReturn(new ExternalContentSyncResult(2, 3, 1, null));
    ExecutionContext executionContext = new ExecutionContext();
    ChunkContext chunkContext = chunkContext(executionContext);
    ContentExternalSyncTasklet tasklet = new ContentExternalSyncTasklet(contentExternalSyncService);

    RepeatStatus status = tasklet.execute(null, chunkContext);

    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.CREATED_COUNT_KEY)).isEqualTo(2);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.SKIPPED_COUNT_KEY)).isEqualTo(3);
    assertThat(executionContext.getInt(ContentExternalSyncTasklet.FAILED_COUNT_KEY)).isEqualTo(1);
    assertThat(executionContext.containsKey(ContentExternalSyncTasklet.SYNCED_AT_KEY)).isFalse();
  }

  @Test
  void execute_propagatesSyncFailure() {
    RuntimeException failure = new RuntimeException("external sync failed");
    given(contentExternalSyncService.syncExternalContents()).willThrow(failure);
    ContentExternalSyncTasklet tasklet = new ContentExternalSyncTasklet(contentExternalSyncService);

    assertThatThrownBy(() -> tasklet.execute(null, chunkContext(new ExecutionContext())))
        .isSameAs(failure);
  }

  private ChunkContext chunkContext(ExecutionContext executionContext) {
    JobExecution jobExecution = new JobExecution(1L);
    jobExecution.setExecutionContext(executionContext);
    StepExecution stepExecution = new StepExecution(ContentExternalSyncJobConfig.STEP_NAME, jobExecution);
    return new ChunkContext(new StepContext(stepExecution));
  }
}
