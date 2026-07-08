package io.mopl.domain.content.batch;

import io.mopl.global.scheduler.BatchTask;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContentExternalSyncBatchTask implements BatchTask {

  private final Job contentExternalSyncJob;

  @Value("${mopl.content.batch.external-sync.cron:0 0 3 * * *}")
  private String cron;

  public ContentExternalSyncBatchTask(@Qualifier("contentExternalSyncJob") Job contentExternalSyncJob) {
    this.contentExternalSyncJob = contentExternalSyncJob;
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
}
