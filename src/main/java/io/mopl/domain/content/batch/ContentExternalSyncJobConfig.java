package io.mopl.domain.content.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ContentExternalSyncJobConfig {

  public static final String JOB_NAME = "contentExternalSyncJob";
  public static final String STEP_NAME = "contentExternalSyncStep";

  private final JobRepository jobRepository;
  private final ContentExternalSyncTasklet contentExternalSyncTasklet;

  @Bean
  public Job contentExternalSyncJob() {
    return new JobBuilder(JOB_NAME, jobRepository)
        .start(contentExternalSyncStep())
        .build();
  }

  @Bean
  public Step contentExternalSyncStep() {
    return new StepBuilder(STEP_NAME, jobRepository)
        .tasklet(contentExternalSyncTasklet, new ResourcelessTransactionManager())
        .build();
  }
}