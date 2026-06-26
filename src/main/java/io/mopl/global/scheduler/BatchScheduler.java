package io.mopl.global.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.ZoneId;
import java.util.List;
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

    Timer timer = Timer.builder("mopl.batch.execution.time")
        .description("배치 작업 소요 시간")
        .tag("jobName", jobName)
        .register(meterRegistry);

    try {
      Job job = task.getJob();

      JobParameters params = new JobParametersBuilder()
          .addLong("runTime", System.currentTimeMillis())
          .toJobParameters();

      JobExecution execution = timer.recordCallable(() -> jobLauncher.run(job, params));

      if (execution != null && execution.getStatus() == BatchStatus.COMPLETED) {
        meterRegistry.counter("mopl.batch.execution.status", "jobName", jobName, "status", "SUCCESS").increment();
        log.info("[Spring Batch] 실행 성공 JobName: {}", jobName);
      } else {
        // Step 중 하나라도 실패해서 Job이 FAILED 상태로 끝난 경우
        meterRegistry.counter("mopl.batch.execution.status", "jobName", jobName, "status", "FAIL").increment();
        log.warn("[Spring Batch] 실행 실패 또는 중단 JobName: {}", jobName);
      }

    } catch (Exception e) {
      meterRegistry.counter("mopl.batch.execution.status", "jobName", jobName, "status", "FAIL").increment();
      log.error("[Spring Batch] 실행 중 예외 발생 JobName: {}", jobName, e);
    }
  }
}
