package io.mopl.domain.content.batch;

import io.mopl.global.scheduler.BatchTask;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "mopl.content.batch.thumbnail-cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ContentThumbnailCleanupBatchTask implements BatchTask {

  private final Job contentThumbnailCleanupJob;
  private final String cron;

  public ContentThumbnailCleanupBatchTask(
      @Qualifier("contentThumbnailCleanupJob") Job contentThumbnailCleanupJob,
      @Value("${mopl.content.batch.thumbnail-cleanup.cron:0 30 4 * * *}") String cron
  ) {
    this.contentThumbnailCleanupJob = contentThumbnailCleanupJob;
    this.cron = validateCron(cron);
  }

  @Override
  public String getCron() {
    return cron;
  }

  @Override
  public String getJobName() {
    return contentThumbnailCleanupJob.getName();
  }

  @Override
  public Job getJob() {
    return contentThumbnailCleanupJob;
  }

  private static String validateCron(String cron) {
    if (cron == null || cron.isBlank()) {
      throw new IllegalArgumentException("콘텐츠 썸네일 정리 cron은 필수입니다.");
    }
    String normalizedCron = cron.trim();
    CronExpression.parse(normalizedCron);
    return normalizedCron;
  }
}
