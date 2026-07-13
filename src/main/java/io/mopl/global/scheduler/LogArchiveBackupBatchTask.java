package io.mopl.global.scheduler;

import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "mopl.logging.backup.enabled",
    havingValue = "true"
)
public class LogArchiveBackupBatchTask implements BatchTask {

  private final Job logArchiveBackupJob;
  private final String cron;

  public LogArchiveBackupBatchTask(
      @Qualifier("logArchiveBackupJob") Job logArchiveBackupJob,
      @Value("${mopl.logging.backup.cron:0 0 4 * * *}") String cron
  ) {
    this.logArchiveBackupJob = logArchiveBackupJob;
    this.cron = validateCron(cron);
  }

  @Override
  public String getCron() {
    return cron;
  }

  @Override
  public String getJobName() {
    return logArchiveBackupJob.getName();
  }

  @Override
  public Job getJob() {
    return logArchiveBackupJob;
  }

  private static String validateCron(String cron) {
    if (cron == null || cron.isBlank()) {
      throw new IllegalArgumentException("로그 아카이브 백업 cron은 필수입니다.");
    }
    String normalizedCron = cron.trim();
    CronExpression.parse(normalizedCron);
    return normalizedCron;
  }
}
