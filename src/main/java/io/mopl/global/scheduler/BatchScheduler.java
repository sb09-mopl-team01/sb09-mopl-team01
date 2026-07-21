package io.mopl.global.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableScheduling
public class BatchScheduler implements SchedulingConfigurer {

  private final List<BatchTask> batchTaskList;

  private final JobLauncher jobLauncher;
  private final MeterRegistry meterRegistry;

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    for (BatchTask task : batchTaskList) {
      taskRegistrar.addTriggerTask(
          () -> executeAsSpringBatchJob(task),
          new CronTrigger(task.getCron(), ZoneId.of("Asia/Seoul"))
      );
    }
  }

  private void executeAsSpringBatchJob(BatchTask task) {
    String jobName = task.getJobName();
    String requestId = UUID.randomUUID().toString();
    long startedAtNanos = System.nanoTime();

    Timer timer = Timer.builder("mopl.batch.execution.time")
        .description("Batch job execution duration")
        .tag("jobName", jobName)
        .register(meterRegistry);

    try {
      Job job = task.getJob();

      JobParameters params = new JobParametersBuilder()
          .addLong("runTime", System.currentTimeMillis())
          .addString("requestId", requestId)
          .toJobParameters();

      log.info("Batch execution started. jobName={}, requestId={}", jobName, requestId);
      JobExecution execution = timer.recordCallable(() -> jobLauncher.run(job, params));
      long durationMs = elapsedMillis(startedAtNanos);
      Long jobExecutionId = execution == null ? null : execution.getId();
      BatchStatus batchStatus = execution == null ? BatchStatus.UNKNOWN : execution.getStatus();

      if (batchStatus == BatchStatus.COMPLETED) {
        meterRegistry.counter("mopl.batch.execution.status", "jobName", jobName, "status", "SUCCESS").increment();
        log.info(
            "Batch execution completed. jobName={}, jobExecutionId={}, requestId={}, status={}, durationMs={}",
            jobName,
            jobExecutionId,
            requestId,
            batchStatus,
            durationMs
        );
      } else {
        meterRegistry.counter("mopl.batch.execution.status", "jobName", jobName, "status", "FAIL").increment();
        log.warn(
            "Batch execution failed. jobName={}, jobExecutionId={}, requestId={}, status={}, durationMs={}",
            jobName,
            jobExecutionId,
            requestId,
            batchStatus,
            durationMs
        );
      }

    } catch (Exception e) {
      meterRegistry.counter("mopl.batch.execution.status", "jobName", jobName, "status", "FAIL").increment();
      log.error(
          "Batch execution failed. jobName={}, requestId={}, durationMs={}, errorType={}, message={}",
          jobName,
          requestId,
          elapsedMillis(startedAtNanos),
          e.getClass().getSimpleName(),
          e.getMessage(),
          e
      );
    }
  }

  private long elapsedMillis(long startedAtNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
  }
}
