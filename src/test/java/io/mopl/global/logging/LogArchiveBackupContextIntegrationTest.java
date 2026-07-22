package io.mopl.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import io.awspring.cloud.s3.S3Template;
import io.mopl.global.config.BaseIntegrationTest;
import io.mopl.global.scheduler.BatchTask;
import io.mopl.global.scheduler.LogArchiveBackupBatchTask;
import io.mopl.global.scheduler.LogArchiveBackupJobConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.Job;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "mopl.logging.backup.enabled=true",
    "mopl.logging.backup.cron=0 0 4 * * *",
    "mopl.logging.backup.archive-directory=build/test-log-archives",
    "mopl.logging.backup.s3-key-prefix=logs/mopl",
    "mopl.logging.backup.instance-id=test-instance",
    "spring.cloud.aws.region.static=ap-northeast-2",
    "spring.batch.job.enabled=false"
})
class LogArchiveBackupContextIntegrationTest extends BaseIntegrationTest {

  @MockitoBean
  private S3Template s3Template;

  @Autowired
  @Qualifier("logArchiveBackupJob")
  private Job logArchiveBackupJob;

  @Autowired
  private LogArchiveStorage logArchiveStorage;

  @Autowired
  private List<BatchTask> batchTasks;

  @Test
  void backupEnabled_registersStorageJobAndScheduledTaskWithoutStartupExecution() {
    assertThat(logArchiveStorage).isNotNull();
    assertThat(logArchiveBackupJob.getName()).isEqualTo(LogArchiveBackupJobConfig.JOB_NAME);
    assertThat(batchTasks)
        .anyMatch(LogArchiveBackupBatchTask.class::isInstance);
    verifyNoInteractions(s3Template);
  }
}
