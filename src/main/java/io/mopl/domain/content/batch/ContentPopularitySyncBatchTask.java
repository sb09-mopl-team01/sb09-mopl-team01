package io.mopl.domain.content.batch;

import io.mopl.global.scheduler.BatchTask;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class ContentPopularitySyncBatchTask implements BatchTask {

  private final Job contentPopularitySyncJob;
  private final String cron;

  public ContentPopularitySyncBatchTask(
      @Qualifier("contentPopularitySyncJob") Job contentPopularitySyncJob,
      @Value("${mopl.content.batch.popularity-sync.cron:0 0 * * * *}") String cron
  ) {
    this.contentPopularitySyncJob = contentPopularitySyncJob;
    this.cron = validateCron(cron);
  }

  @Override
  public String getCron() {
    return cron;
  }

  @Override
  public String getJobName() {
    return contentPopularitySyncJob.getName();
  }

  @Override
  public Job getJob() {
    return contentPopularitySyncJob;
  }

  private static String validateCron(String cron) {
    if (cron == null || cron.isBlank()) {
      throw new IllegalArgumentException("콘텐츠 인기순 보정 cron은 필수입니다.");
    }
    String normalizedCron = cron.trim();
    CronExpression.parse(normalizedCron);
    return normalizedCron;
  }
}
