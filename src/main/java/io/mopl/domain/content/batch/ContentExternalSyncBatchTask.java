package io.mopl.domain.content.batch;

import io.mopl.global.scheduler.BatchTask;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class ContentExternalSyncBatchTask implements BatchTask {

  private final Job contentExternalSyncJob;
  private final String cron;

  public ContentExternalSyncBatchTask(
      @Qualifier("contentExternalSyncJob") Job contentExternalSyncJob,
      @Value("${mopl.content.batch.external-sync.cron:0 0 3 * * *}") String cron
  ) {
    this.contentExternalSyncJob = contentExternalSyncJob;
    this.cron = validateCron(cron);
  }

  @Override
  public String getCron() {
    return cron;
  }

  @Override
  public String getJobName() {
    return contentExternalSyncJob.getName();
  }

  @Override
  public Job getJob() {
    return contentExternalSyncJob;
  }

  private static String validateCron(String cron) {
    if (cron == null || cron.isBlank()) {
      throw new IllegalArgumentException("콘텐츠 외부 동기화 cron은 필수입니다.");
    }
    String normalizedCron = cron.trim();
    CronExpression.parse(normalizedCron);
    return normalizedCron;
  }
}
