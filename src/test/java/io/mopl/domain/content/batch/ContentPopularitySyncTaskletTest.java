package io.mopl.domain.content.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.service.ContentSearchIndexService;
import io.mopl.domain.content.service.ContentSearchIndexService.WatcherCountSyncResult;
import java.util.List;
import java.util.UUID;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ContentPopularitySyncTaskletTest {

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentSearchIndexService contentSearchIndexService;

  @Test
  void synchronizesAllActiveContentsAndStoresResult() throws Exception {
    UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    List<UUID> contentIds = List.of(firstId, secondId);
    PageRequest pageRequest = PageRequest.of(0, 2);
    given(contentRepository.findActiveIdsAfter(null, pageRequest)).willReturn(contentIds);
    given(contentRepository.findActiveIdsAfter(secondId, pageRequest)).willReturn(List.of());
    given(contentSearchIndexService.synchronizeWatcherCounts(contentIds))
        .willReturn(new WatcherCountSyncResult(2, 1, 0, 1));
    ExecutionContext executionContext = new ExecutionContext();

    RepeatStatus status = tasklet(2).execute(null, chunkContext(executionContext));

    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    assertThat(executionContext.getInt(ContentPopularitySyncTasklet.PROCESSED_COUNT_KEY))
        .isEqualTo(2);
    assertThat(executionContext.getInt(ContentPopularitySyncTasklet.UPDATED_COUNT_KEY))
        .isEqualTo(1);
    assertThat(executionContext.getInt(ContentPopularitySyncTasklet.INDEXED_COUNT_KEY))
        .isZero();
    assertThat(executionContext.getInt(ContentPopularitySyncTasklet.UNCHANGED_COUNT_KEY))
        .isEqualTo(1);
    verify(contentSearchIndexService).synchronizeWatcherCounts(contentIds);
  }

  @Test
  void rejectsInvalidChunkSize() {
    assertThatThrownBy(() -> tasklet(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> tasklet(1001))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ContentPopularitySyncTasklet tasklet(int chunkSize) {
    return new ContentPopularitySyncTasklet(
        contentRepository,
        contentSearchIndexService,
        chunkSize
    );
  }

  private ChunkContext chunkContext(ExecutionContext executionContext) {
    JobExecution jobExecution = new JobExecution(1L);
    jobExecution.setExecutionContext(executionContext);
    StepExecution stepExecution = new StepExecution(
        ContentPopularitySyncJobConfig.STEP_NAME,
        jobExecution
    );
    return new ChunkContext(new StepContext(stepExecution));
  }
}
