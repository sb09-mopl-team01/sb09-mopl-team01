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
public class ContentPopularitySyncJobConfig {

  public static final String JOB_NAME = "contentPopularitySyncJob";
  public static final String STEP_NAME = "contentPopularitySyncStep";

  private final JobRepository jobRepository;
  private final ContentPopularitySyncTasklet contentPopularitySyncTasklet;

  @Bean
  public Job contentPopularitySyncJob() {
    return new JobBuilder(JOB_NAME, jobRepository)
        .start(contentPopularitySyncStep())
        .build();
  }

  @Bean
  public Step contentPopularitySyncStep() {
    return new StepBuilder(STEP_NAME, jobRepository)
        .tasklet(contentPopularitySyncTasklet, new ResourcelessTransactionManager())
        .build();
  }
}
