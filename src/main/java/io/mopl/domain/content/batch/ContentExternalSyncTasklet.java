package io.mopl.domain.content.batch;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.service.ContentExternalSyncService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
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
  static final String CREATED_COUNT_KEY = "contentExternalSync.createdCount";
  static final String SKIPPED_COUNT_KEY = "contentExternalSync.skippedCount";
  static final String FAILED_COUNT_KEY = "contentExternalSync.failedCount";
  static final String SYNCED_AT_KEY = "contentExternalSync.syncedAt";

  private final ContentExternalSyncService contentExternalSyncService;

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    log.info("Content externalSync started.");
    try {
      ExternalContentSyncResult result = contentExternalSyncService.syncExternalContents();
      putResultToExecutionContext(chunkContext, result);
      log.info(
          "Content externalSync completed. fetchedCount={}, createdCount={}, skippedCount={}, failedCount={}, syncedAt={}",
          result.fetchedCount(),
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

  private void putResultToExecutionContext(ChunkContext chunkContext, ExternalContentSyncResult result) {
    ExecutionContext jobExecutionContext = chunkContext.getStepContext()
        .getStepExecution()
        .getJobExecution()
        .getExecutionContext();

    jobExecutionContext.putInt(FETCHED_COUNT_KEY, result.fetchedCount());
    jobExecutionContext.putInt(CREATED_COUNT_KEY, result.createdCount());
    jobExecutionContext.putInt(SKIPPED_COUNT_KEY, result.skippedCount());
    jobExecutionContext.putInt(FAILED_COUNT_KEY, result.failedCount());
    Instant syncedAt = result.syncedAt();
    if (syncedAt != null) {
      jobExecutionContext.putString(SYNCED_AT_KEY, syncedAt.toString());
    }
  }
}