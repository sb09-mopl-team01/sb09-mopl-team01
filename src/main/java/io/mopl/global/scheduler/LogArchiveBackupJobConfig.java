package io.mopl.global.scheduler;

import io.mopl.global.logging.LogArchiveBackupTasklet;
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
    name = "mopl.logging.backup.enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
public class LogArchiveBackupJobConfig {

  public static final String JOB_NAME = "logArchiveBackupJob";
  public static final String STEP_NAME = "logArchiveBackupStep";

  private final JobRepository jobRepository;
  private final LogArchiveBackupTasklet logArchiveBackupTasklet;

  @Bean
  public Job logArchiveBackupJob() {
    return new JobBuilder(JOB_NAME, jobRepository)
        .start(logArchiveBackupStep())
        .build();
  }

  @Bean
  public Step logArchiveBackupStep() {
    return new StepBuilder(STEP_NAME, jobRepository)
        .tasklet(logArchiveBackupTasklet, new ResourcelessTransactionManager())
        .build();
  }
}
