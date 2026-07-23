package io.mopl.domain.content.batch;

import io.mopl.domain.content.dto.ContentThumbnailCleanupResult;
import io.mopl.domain.content.service.ContentThumbnailCleanupService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "mopl.content.batch.thumbnail-cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@Slf4j
public class ContentThumbnailCleanupTasklet implements Tasklet {

  static final String DISCOVERED_COUNT_KEY = "contentThumbnailCleanup.discoveredCount";
  static final String CLEANED_COUNT_KEY = "contentThumbnailCleanup.cleanedCount";
  static final String SKIPPED_COUNT_KEY = "contentThumbnailCleanup.skippedCount";
  static final String FAILED_COUNT_KEY = "contentThumbnailCleanup.failedCount";
  static final String CUTOFF_KEY = "contentThumbnailCleanup.cutoff";

  private final ContentThumbnailCleanupService cleanupService;
  private final JobExplorer jobExplorer;
  private final Clock clock;
  private final int retentionDays;
  private final int chunkSize;

  public ContentThumbnailCleanupTasklet(
      ContentThumbnailCleanupService cleanupService,
      JobExplorer jobExplorer,
      Clock clock,
      @Value("${mopl.content.batch.thumbnail-cleanup.retention-days:90}") int retentionDays,
      @Value("${mopl.content.batch.thumbnail-cleanup.chunk-size:100}") int chunkSize
  ) {
    if (retentionDays <= 0) {
      throw new IllegalArgumentException("콘텐츠 썸네일 보존 기간은 1일 이상이어야 합니다.");
    }
    if (chunkSize <= 0 || chunkSize > 1000) {
      throw new IllegalArgumentException("콘텐츠 썸네일 정리 청크 크기는 1~1000이어야 합니다.");
    }
    this.cleanupService = cleanupService;
    this.jobExplorer = jobExplorer;
    this.clock = clock;
    this.retentionDays = retentionDays;
    this.chunkSize = chunkSize;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    ensureNoOlderRunningExecution(chunkContext);
    Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
    log.info(
        "Content thumbnailCleanup started. cutoff={}, retentionDays={}, chunkSize={}",
        cutoff,
        retentionDays,
        chunkSize
    );

    ContentThumbnailCleanupResult result = cleanupService.cleanupExpiredThumbnails(
        cutoff,
        chunkSize
    );
    putResultToExecutionContext(chunkContext, cutoff, result);

    if (result.failedCount() > 0) {
      log.error(
          "Content thumbnailCleanup failed. discoveredCount={}, cleanedCount={}, skippedCount={}, failedCount={}, cutoff={}",
          result.discoveredCount(),
          result.cleanedCount(),
          result.skippedCount(),
          result.failedCount(),
          cutoff
      );
      throw new IllegalStateException("콘텐츠 썸네일 정리 중 실패한 항목이 있습니다.");
    }

    log.info(
        "Content thumbnailCleanup completed. discoveredCount={}, cleanedCount={}, skippedCount={}, failedCount={}, cutoff={}",
        result.discoveredCount(),
        result.cleanedCount(),
        result.skippedCount(),
        result.failedCount(),
        cutoff
    );
    return RepeatStatus.FINISHED;
  }

  private void ensureNoOlderRunningExecution(ChunkContext chunkContext) {
    JobExecution currentExecution = chunkContext.getStepContext()
        .getStepExecution()
        .getJobExecution();
    Long currentExecutionId = currentExecution.getId();
    boolean olderExecutionExists = jobExplorer
        .findRunningJobExecutions(ContentThumbnailCleanupJobConfig.JOB_NAME)
        .stream()
        .map(JobExecution::getId)
        .filter(executionId -> executionId != null && currentExecutionId != null)
        .anyMatch(executionId -> executionId < currentExecutionId);

    if (olderExecutionExists) {
      log.warn(
          "Content thumbnailCleanup rejected. reason=alreadyRunning, jobExecutionId={}",
          currentExecutionId
      );
      throw new IllegalStateException("콘텐츠 썸네일 정리 Job이 이미 실행 중입니다.");
    }
  }

  private void putResultToExecutionContext(
      ChunkContext chunkContext,
      Instant cutoff,
      ContentThumbnailCleanupResult result
  ) {
    ExecutionContext executionContext = chunkContext.getStepContext()
        .getStepExecution()
        .getJobExecution()
        .getExecutionContext();
    executionContext.putInt(DISCOVERED_COUNT_KEY, result.discoveredCount());
    executionContext.putInt(CLEANED_COUNT_KEY, result.cleanedCount());
    executionContext.putInt(SKIPPED_COUNT_KEY, result.skippedCount());
    executionContext.putInt(FAILED_COUNT_KEY, result.failedCount());
    executionContext.putString(CUTOFF_KEY, cutoff.toString());
  }
}
