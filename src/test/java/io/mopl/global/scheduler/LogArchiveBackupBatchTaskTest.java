package io.mopl.global.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import io.mopl.global.config.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;

@ExtendWith(MockitoExtension.class)
class LogArchiveBackupBatchTaskTest extends BaseIntegrationTest {

  @Mock
  private Job logArchiveBackupJob;

  @Test
  void providesCronJobNameAndJob() {
    given(logArchiveBackupJob.getName()).willReturn(LogArchiveBackupJobConfig.JOB_NAME);
    LogArchiveBackupBatchTask batchTask = new LogArchiveBackupBatchTask(
        logArchiveBackupJob,
        "0 0 4 * * *"
    );

    assertThat(batchTask.getCron()).isEqualTo("0 0 4 * * *");
    assertThat(batchTask.getJob()).isSameAs(logArchiveBackupJob);
    assertThat(batchTask.getJobName()).isEqualTo(LogArchiveBackupJobConfig.JOB_NAME);
  }

  @Test
  void rejectsInvalidCronAtStartup() {
    assertThatThrownBy(() -> new LogArchiveBackupBatchTask(logArchiveBackupJob, "invalid-cron"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
