package io.mopl.domain.content.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    name = "mopl.content.batch.thumbnail-cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
public class ContentThumbnailCleanupJobConfig {

  public static final String JOB_NAME = "contentThumbnailCleanupJob";
  public static final String STEP_NAME = "contentThumbnailCleanupStep";

  private final JobRepository jobRepository;
  private final ContentThumbnailCleanupTasklet contentThumbnailCleanupTasklet;

  @Bean
  public Job contentThumbnailCleanupJob() {
    return new JobBuilder(JOB_NAME, jobRepository)
        .start(contentThumbnailCleanupStep())
        .build();
  }

  @Bean
  public Step contentThumbnailCleanupStep() {
    return new StepBuilder(STEP_NAME, jobRepository)
        .tasklet(contentThumbnailCleanupTasklet, new ResourcelessTransactionManager())
        .build();
  }
}
