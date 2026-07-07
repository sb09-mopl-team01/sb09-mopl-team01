package io.mopl.domain.content.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentExternalSyncBatchTaskTest {

  @Mock
  private Job contentExternalSyncJob;

  @Test
  void providesCronJobNameAndJob() {
    given(contentExternalSyncJob.getName()).willReturn(ContentExternalSyncJobConfig.JOB_NAME);
    ContentExternalSyncBatchTask batchTask = new ContentExternalSyncBatchTask(contentExternalSyncJob);
    ReflectionTestUtils.setField(batchTask, "cron", "0 0 3 * * *");

    assertThat(batchTask.getCron()).isEqualTo("0 0 3 * * *");
    assertThat(batchTask.getJob()).isSameAs(contentExternalSyncJob);
    assertThat(batchTask.getJobName()).isEqualTo(ContentExternalSyncJobConfig.JOB_NAME);
  }
}