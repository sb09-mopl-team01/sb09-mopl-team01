package io.mopl.domain.content.batch;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.service.ContentExternalSyncService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentExternalSyncTasklet implements Tasklet {

  static final String FETCHED_COUNT_KEY = "contentExternalSync.fetchedCount";
  static final String ACCEPTED_COUNT_KEY = "contentExternalSync.acceptedCount";
  static final String FILTERED_COUNT_KEY = "contentExternalSync.filteredCount";
  static final String CREATED_COUNT_KEY = "contentExternalSync.createdCount";
  static final String SKIPPED_COUNT_KEY = "contentExternalSync.skippedCount";
  static final String FAILED_COUNT_KEY = "contentExternalSync.failedCount";
  static final String SYNCED_AT_KEY = "contentExternalSync.syncedAt";

  private final ContentExternalSyncService contentExternalSyncService;
  private final JobExplorer jobExplorer;
  private final ContentExternalSyncMetrics contentExternalSyncMetrics;

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    ensureNoOlderRunningExecution(chunkContext);
    log.info("Content externalSync started.");
    try {
      ExternalContentSyncResult result = contentExternalSyncService.syncExternalContents();
      putResultToExecutionContext(chunkContext, result);
      contentExternalSyncMetrics.record(result);
      log.info(
          "Content externalSync completed. fetchedCount={}, acceptedCount={}, filteredCount={}, createdCount={}, skippedCount={}, failedCount={}, syncedAt={}",
          result.fetchedCount(),
          result.acceptedCount(),
          result.filteredCount(),
          result.createdCount(),
          result.skippedCount(),
          result.failedCount(),
          result.syncedAt()
      );
      return RepeatStatus.FINISHED;
    } catch (RuntimeException e) {
      log.error(
          "Content externalSync failed. errorType={}, message={}",
          e.getClass().getSimpleName(),
          e.getMessage(),
          e
      );
      throw e;
    }
  }

  private void ensureNoOlderRunningExecution(ChunkContext chunkContext) {
    JobExecution currentExecution = chunkContext.getStepContext()
        .getStepExecution()
        .getJobExecution();
    Long currentExecutionId = currentExecution.getId();
    boolean olderExecutionExists = jobExplorer.findRunningJobExecutions(ContentExternalSyncJobConfig.JOB_NAME)
        .stream()
        .map(JobExecution::getId)
        .filter(executionId -> executionId != null && currentExecutionId != null)
        .anyMatch(executionId -> executionId < currentExecutionId);

    if (olderExecutionExists) {
      log.warn(
          "Content externalSync rejected. reason=alreadyRunning, jobExecutionId={}",
          currentExecutionId
      );
      throw new IllegalStateException("콘텐츠 외부 동기화 Job이 이미 실행 중입니다.");
    }
  }

  private void putResultToExecutionContext(ChunkContext chunkContext, ExternalContentSyncResult result) {
    ExecutionContext jobExecutionContext = chunkContext.getStepContext()
        .getStepExecution()
        .getJobExecution()
        .getExecutionContext();

    jobExecutionContext.putInt(FETCHED_COUNT_KEY, result.fetchedCount());
    jobExecutionContext.putInt(ACCEPTED_COUNT_KEY, result.acceptedCount());
    jobExecutionContext.putInt(FILTERED_COUNT_KEY, result.filteredCount());
    jobExecutionContext.putInt(CREATED_COUNT_KEY, result.createdCount());
    jobExecutionContext.putInt(SKIPPED_COUNT_KEY, result.skippedCount());
    jobExecutionContext.putInt(FAILED_COUNT_KEY, result.failedCount());
    Instant syncedAt = result.syncedAt();
    if (syncedAt != null) {
      jobExecutionContext.putString(SYNCED_AT_KEY, syncedAt.toString());
    }
  }
}
