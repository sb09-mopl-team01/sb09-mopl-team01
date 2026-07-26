package io.mopl.domain.content.batch;

import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.service.ContentSearchIndexService;
import io.mopl.domain.content.service.ContentSearchIndexService.WatcherCountSyncResult;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ContentPopularitySyncTasklet implements Tasklet {

  static final String PROCESSED_COUNT_KEY = "contentPopularitySync.processedCount";
  static final String UPDATED_COUNT_KEY = "contentPopularitySync.updatedCount";
  static final String INDEXED_COUNT_KEY = "contentPopularitySync.indexedCount";
  static final String UNCHANGED_COUNT_KEY = "contentPopularitySync.unchangedCount";

  private final ContentRepository contentRepository;
  private final ContentSearchIndexService contentSearchIndexService;
  private final int chunkSize;

  public ContentPopularitySyncTasklet(
      ContentRepository contentRepository,
      ContentSearchIndexService contentSearchIndexService,
      @Value("${mopl.content.batch.popularity-sync.chunk-size:500}") int chunkSize
  ) {
    if (chunkSize <= 0 || chunkSize > 1000) {
      throw new IllegalArgumentException(
          "콘텐츠 인기순 보정 청크 크기는 1~1000이어야 합니다."
      );
    }
    this.contentRepository = contentRepository;
    this.contentSearchIndexService = contentSearchIndexService;
    this.chunkSize = chunkSize;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    int processedCount = 0;
    int updatedCount = 0;
    int indexedCount = 0;
    int unchangedCount = 0;
    UUID idAfter = null;

    log.info("Content popularitySync started. chunkSize={}", chunkSize);
    while (true) {
      List<UUID> contentIds = contentRepository.findActiveIdsAfter(
          idAfter,
          PageRequest.of(0, chunkSize)
      );
      if (contentIds.isEmpty()) {
        break;
      }

      WatcherCountSyncResult result = contentSearchIndexService
          .synchronizeWatcherCounts(contentIds);
      processedCount += result.processedCount();
      updatedCount += result.updatedCount();
      indexedCount += result.indexedCount();
      unchangedCount += result.unchangedCount();
      idAfter = contentIds.get(contentIds.size() - 1);

      if (contentIds.size() < chunkSize) {
        break;
      }
    }

    ExecutionContext executionContext = chunkContext.getStepContext()
        .getStepExecution()
        .getJobExecution()
        .getExecutionContext();
    executionContext.putInt(PROCESSED_COUNT_KEY, processedCount);
    executionContext.putInt(UPDATED_COUNT_KEY, updatedCount);
    executionContext.putInt(INDEXED_COUNT_KEY, indexedCount);
    executionContext.putInt(UNCHANGED_COUNT_KEY, unchangedCount);

    log.info(
        "Content popularitySync completed. processedCount={}, updatedCount={}, "
            + "indexedCount={}, unchangedCount={}",
        processedCount,
        updatedCount,
        indexedCount,
        unchangedCount
    );
    return RepeatStatus.FINISHED;
  }
}
