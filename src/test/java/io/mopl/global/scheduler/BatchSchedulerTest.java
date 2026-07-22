package io.mopl.global.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BatchSchedulerTest {

  @Mock
  private JobLauncher jobLauncher;

  @Mock
  private BatchTask mockBatchTask;

  @Mock
  private Job mockJob;

  @Mock
  private ScheduledTaskRegistrar taskRegistrar;

  @Mock
  private RedissonClient redissonClient;

  @Mock
  private RLock mockLock;

  private MeterRegistry meterRegistry;
  private BatchScheduler batchScheduler;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    batchScheduler = new BatchScheduler(List.of(mockBatchTask), jobLauncher, meterRegistry, redissonClient);

    lenient().when(mockBatchTask.getJobName()).thenReturn("testJob");
    given(mockBatchTask.getCron()).willReturn("0 0 0 * * *");
    lenient().when(mockBatchTask.getJob()).thenReturn(mockJob);

    lenient().when(redissonClient.getLock("lock:batch:testJob")).thenReturn(mockLock);
  }

  @AfterEach
  void tearDown() {
    Thread.interrupted();
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
  @DisplayName("배치 작업 성공 시 SUCCESS 카운터가 증가하고 락이 해제된다")
  void executeJob_Success_IncrementsSuccessCounter(CapturedOutput output) throws Exception {
    Runnable scheduledAction = captureScheduledAction();

    given(mockLock.tryLock(0, -1, TimeUnit.SECONDS)).willReturn(true);
    given(mockLock.isHeldByCurrentThread()).willReturn(true);

    JobExecution successExecution = new JobExecution(1L);
    successExecution.setStatus(BatchStatus.COMPLETED);
    given(jobLauncher.run(any(Job.class), any(JobParameters.class))).willReturn(successExecution);

    scheduledAction.run();

    double successCount = meterRegistry.counter("mopl.batch.execution.status", "jobName", "testJob", "status", "SUCCESS").count();
    assertThat(successCount).isEqualTo(1.0);
    assertThat(output)
        .contains("Successfully acquired Redis lock")
        .contains("Batch execution started. jobName=testJob")
        .contains("Batch execution completed. jobName=testJob, jobExecutionId=1")
        .contains("status=COMPLETED");

    verify(mockLock).unlock();
  }

  @Test
  @DisplayName("Redis 락 획득 실패 시 배치를 실행하지 않고 건너뛴다")
  void executeJob_LockAcquisitionFailed_SkipsExecution(CapturedOutput output) throws Exception {
    Runnable scheduledAction = captureScheduledAction();

    given(mockLock.tryLock(0, -1, TimeUnit.SECONDS)).willReturn(false);

    scheduledAction.run();

    verify(jobLauncher, never()).run(any(), any());

    assertThat(output).contains("Batch job is already running on another instance. Skipping. jobName=testJob");
  }

  @Test
  @DisplayName("Redis 락 획득 중 인터럽트 발생 시 에러를 로그에 남기고 실행을 중단한다")
  void executeJob_InterruptedWhileLocking_LogsError(CapturedOutput output) throws Exception {
    Runnable scheduledAction = captureScheduledAction();

    given(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willThrow(new InterruptedException("Interrupted"));

    scheduledAction.run();

    verify(jobLauncher, never()).run(any(), any());
    assertThat(output).contains("Interrupted while acquiring Redis lock. jobName=testJob");

    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  @DisplayName("배치 작업 실행 시 중복 실행 방지를 위한 고유 JobParameter를 전달한다")
  void executeJob_PassesUniqueJobParameters() throws Exception {
    Runnable scheduledAction = captureScheduledAction();

    given(mockLock.tryLock(0, -1, TimeUnit.SECONDS)).willReturn(true);
    given(mockLock.isHeldByCurrentThread()).willReturn(true);

    JobExecution successExecution = new JobExecution(1L);
    successExecution.setStatus(BatchStatus.COMPLETED);
    given(jobLauncher.run(any(Job.class), any(JobParameters.class))).willReturn(successExecution);

    scheduledAction.run();

    ArgumentCaptor<JobParameters> parametersCaptor = ArgumentCaptor.forClass(JobParameters.class);
    verify(jobLauncher).run(any(Job.class), parametersCaptor.capture());
    JobParameters jobParameters = parametersCaptor.getValue();

    assertThat(jobParameters.getLong("runTime")).isNotNull();
    assertThat(jobParameters.getString("requestId")).isNotBlank();
  }

  @Test
  @DisplayName("배치 작업 실패(FAILED 상태) 시 FAIL 카운터가 증가하고 락이 해제된다")
  void executeJob_FailedStatus_IncrementsFailCounter(CapturedOutput output) throws Exception {
    Runnable scheduledAction = captureScheduledAction();

    given(mockLock.tryLock(0, -1, TimeUnit.SECONDS)).willReturn(true);
    given(mockLock.isHeldByCurrentThread()).willReturn(true);

    JobExecution failedExecution = new JobExecution(1L);
    failedExecution.setStatus(BatchStatus.FAILED);
    given(jobLauncher.run(any(), any())).willReturn(failedExecution);

    scheduledAction.run();

    double failCount = meterRegistry.counter("mopl.batch.execution.status", "jobName", "testJob", "status", "FAIL").count();
    assertThat(failCount).isEqualTo(1.0);
    assertThat(output)
        .contains("Batch execution failed. jobName=testJob, jobExecutionId=1")
        .contains("status=FAILED");

    verify(mockLock).unlock();
  }

  @Test
  @DisplayName("배치 작업 중 예외 발생 시 FAIL 카운터가 증가하고 락이 안전하게 해제된다")
  void executeJob_ExceptionThrown_IncrementsFailCounter(CapturedOutput output) throws Exception {
    Runnable scheduledAction = captureScheduledAction();

    // 💡 수정된 파라미터 적용
    given(mockLock.tryLock(0, -1, TimeUnit.SECONDS)).willReturn(true);
    given(mockLock.isHeldByCurrentThread()).willReturn(true);

    given(jobLauncher.run(any(), any())).willThrow(new RuntimeException("Job execution failed"));

    scheduledAction.run();

    double failCount = meterRegistry.counter("mopl.batch.execution.status", "jobName", "testJob", "status", "FAIL").count();
    assertThat(failCount).isEqualTo(1.0);
    assertThat(output)
        .contains("Batch execution failed. jobName=testJob")
        .contains("errorType=RuntimeException")
        .contains("message=Job execution failed");

    verify(mockLock).unlock();
  }

  private Runnable captureScheduledAction() {
    batchScheduler.configureTasks(taskRegistrar);

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(taskRegistrar).addTriggerTask(runnableCaptor.capture(), any(Trigger.class));

    return runnableCaptor.getValue();
  }
}