package io.mopl.global.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.Trigger;

@ExtendWith(MockitoExtension.class)
class BatchSchedulerTest {

  @Mock
  private JobLauncher jobLauncher;

  @Mock
  private BatchTask mockBatchTask;

  @Mock
  private Job mockJob;

  @Mock
  private ScheduledTaskRegistrar taskRegistrar;

  private MeterRegistry meterRegistry;
  private BatchScheduler batchScheduler;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    batchScheduler = new BatchScheduler(List.of(mockBatchTask), jobLauncher, meterRegistry);

    lenient().when(mockBatchTask.getJobName()).thenReturn("testJob");

    given(mockBatchTask.getCron()).willReturn("0 0 0 * * *");
    lenient().when(mockBatchTask.getJob()).thenReturn(mockJob);
  }

  @Test
  @DisplayName("스케줄러에 작업이 올바른 크론 표현식과 함께 등록된다")
  void configureTasks_RegistersTasksCorrectly() {
    batchScheduler.configureTasks(taskRegistrar);

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);

    verify(taskRegistrar).addTriggerTask(runnableCaptor.capture(), triggerCaptor.capture());

    CronTrigger trigger = (CronTrigger) triggerCaptor.getValue();

    assertThat(trigger.getExpression()).isEqualTo("0 0 0 * * *");
  }

  @Test
  @DisplayName("배치 작업 성공 시 SUCCESS 카운터가 증가한다")
  void executeJob_Success_IncrementsSuccessCounter() throws Exception {
    Runnable scheduledAction = captureScheduledAction();

    JobExecution successExecution = new JobExecution(1L);
    successExecution.setStatus(BatchStatus.COMPLETED);
    given(jobLauncher.run(any(Job.class), any(JobParameters.class))).willReturn(successExecution);

    scheduledAction.run();

    double successCount = meterRegistry.counter("mopl.batch.execution.status", "jobName", "testJob", "status", "SUCCESS").count();
    assertThat(successCount).isEqualTo(1.0);
  }

  @Test
  @DisplayName("배치 작업 실패(FAILED 상태) 시 FAIL 카운터가 증가한다")
  void executeJob_FailedStatus_IncrementsFailCounter() throws Exception {
    Runnable scheduledAction = captureScheduledAction();

    JobExecution failedExecution = new JobExecution(1L);
    failedExecution.setStatus(BatchStatus.FAILED);
    given(jobLauncher.run(any(), any())).willReturn(failedExecution);

    scheduledAction.run();

    double failCount = meterRegistry.counter("mopl.batch.execution.status", "jobName", "testJob", "status", "FAIL").count();
    assertThat(failCount).isEqualTo(1.0);
  }

  @Test
  @DisplayName("배치 작업 중 예외 발생 시 FAIL 카운터가 증가한다")
  void executeJob_ExceptionThrown_IncrementsFailCounter() throws Exception {
    Runnable scheduledAction = captureScheduledAction();

    // 예외를 강제로 발생시킴
    given(jobLauncher.run(any(), any())).willThrow(new RuntimeException("Job execution failed"));

    scheduledAction.run();

    double failCount = meterRegistry.counter("mopl.batch.execution.status", "jobName", "testJob", "status", "FAIL").count();
    assertThat(failCount).isEqualTo(1.0);
  }

  private Runnable captureScheduledAction() {
    batchScheduler.configureTasks(taskRegistrar);

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(taskRegistrar).addTriggerTask(runnableCaptor.capture(), any(Trigger.class));

    return runnableCaptor.getValue();
  }
}