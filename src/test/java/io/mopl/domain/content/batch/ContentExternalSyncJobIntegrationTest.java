package io.mopl.domain.content.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.service.ContentExternalSyncService;
import io.mopl.global.config.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@TestPropertySource(properties = "spring.batch.job.enabled=false")
class ContentExternalSyncJobIntegrationTest extends BaseIntegrationTest {

  private static final Instant FIRST_SYNCED_AT = Instant.parse("2026-07-20T00:00:00Z");
  private static final Instant SECOND_SYNCED_AT = Instant.parse("2026-07-21T00:00:00Z");

  @Autowired
  private JobLauncher jobLauncher;

  @Autowired
  @Qualifier("contentExternalSyncJob")
  private Job contentExternalSyncJob;

  @MockitoBean
  private ContentExternalSyncService contentExternalSyncService;

  @Test
  void jobCompletesOnFirstRunAndRerunWithIndependentExecutionResults() throws Exception {
    ExternalContentSyncResult firstResult = new ExternalContentSyncResult(
        1, 1, 0, 1, 0, 0, FIRST_SYNCED_AT
    );
    ExternalContentSyncResult secondResult = new ExternalContentSyncResult(
        1, 1, 0, 0, 1, 0, SECOND_SYNCED_AT
    );
    given(contentExternalSyncService.syncExternalContents())
        .willReturn(firstResult, secondResult);

    JobExecution firstExecution = launch(1L);
    JobExecution secondExecution = launch(2L);

    assertThat(firstExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(firstExecution.getExecutionContext().getInt(
        ContentExternalSyncTasklet.CREATED_COUNT_KEY)).isEqualTo(1);
    assertThat(secondExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(secondExecution.getExecutionContext().getInt(
        ContentExternalSyncTasklet.SKIPPED_COUNT_KEY)).isEqualTo(1);
    assertThat(secondExecution.getExecutionContext().getString(
        ContentExternalSyncTasklet.SYNCED_AT_KEY)).isEqualTo(SECOND_SYNCED_AT.toString());
    verify(contentExternalSyncService, times(2)).syncExternalContents();
  }

  @Test
  void jobRecordsFailedStatusWhenExternalSyncFails() throws Exception {
    IllegalStateException failure = new IllegalStateException("external sync failed");
    given(contentExternalSyncService.syncExternalContents()).willThrow(failure);

    JobExecution execution = launch(3L);

    assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(execution.getAllFailureExceptions())
        .anySatisfy(exception -> assertThat(exception).isSameAs(failure));
  }

  private JobExecution launch(long runId) throws Exception {
    return jobLauncher.run(
        contentExternalSyncJob,
        new JobParametersBuilder()
            .addLong("run.id", runId)
            .toJobParameters()
    );
  }
}
