package io.mopl.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.global.scheduler.LogArchiveBackupJobConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class LogArchiveBackupTaskletTest {

  @Mock
  private LogArchiveStorage logArchiveStorage;

  @Mock
  private JobExplorer jobExplorer;

  @TempDir
  private Path tempDirectory;

  @BeforeEach
  void setUp() {
    lenient().when(jobExplorer.findRunningJobExecutions(LogArchiveBackupJobConfig.JOB_NAME))
        .thenReturn(Set.of());
  }

  @Test
  void execute_uploadsOnlyCompletedLogArchives() throws Exception {
    Path archive = Files.writeString(
        tempDirectory.resolve("mopl.2026-07-12.0.log.gz"),
        "archive"
    );
    Files.writeString(tempDirectory.resolve("mopl.log"), "active");
    Files.writeString(tempDirectory.resolve("README.txt"), "ignored");
    ExecutionContext executionContext = new ExecutionContext();
    LogArchiveBackupTasklet tasklet = tasklet(tempDirectory);

    RepeatStatus result = tasklet.execute(null, chunkContext(executionContext, 2L));

    assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    verify(logArchiveStorage).upload(
        archive,
        "logs/mopl/instance-a/2026/07/12/mopl.2026-07-12.0.log.gz"
    );
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.DISCOVERED_COUNT_KEY)).isEqualTo(1);
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.UPLOADED_COUNT_KEY)).isEqualTo(1);
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.SKIPPED_COUNT_KEY)).isZero();
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.FAILED_COUNT_KEY)).isZero();
    assertThat(Files.exists(archive)).isTrue();
  }

  @Test
  void execute_skipsArchiveThatAlreadyExistsInS3() throws Exception {
    Path archive = Files.writeString(
        tempDirectory.resolve("mopl.2026-07-12.0.log.gz"),
        "archive"
    );
    String objectKey = "logs/mopl/instance-a/2026/07/12/mopl.2026-07-12.0.log.gz";
    given(logArchiveStorage.exists(objectKey)).willReturn(true);
    ExecutionContext executionContext = new ExecutionContext();

    tasklet(tempDirectory).execute(null, chunkContext(executionContext, 2L));

    verify(logArchiveStorage, never()).upload(archive, objectKey);
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.UPLOADED_COUNT_KEY)).isZero();
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.SKIPPED_COUNT_KEY)).isEqualTo(1);
    assertThat(Files.exists(archive)).isTrue();
  }

  @Test
  void execute_continuesOtherUploadsAndFailsJobWhenOneArchiveFails() throws Exception {
    Path failedArchive = Files.writeString(
        tempDirectory.resolve("mopl.2026-07-11.0.log.gz"),
        "failed"
    );
    Path uploadedArchive = Files.writeString(
        tempDirectory.resolve("mopl.2026-07-12.0.log.gz"),
        "uploaded"
    );
    String failedKey = "logs/mopl/instance-a/2026/07/11/mopl.2026-07-11.0.log.gz";
    String uploadedKey = "logs/mopl/instance-a/2026/07/12/mopl.2026-07-12.0.log.gz";
    doThrow(new IOException("s3 unavailable"))
        .when(logArchiveStorage).upload(failedArchive, failedKey);
    ExecutionContext executionContext = new ExecutionContext();

    assertThatThrownBy(
        () -> tasklet(tempDirectory).execute(null, chunkContext(executionContext, 2L))
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("로그 아카이브 백업 중 실패한 파일이 있습니다.");

    verify(logArchiveStorage).upload(uploadedArchive, uploadedKey);
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.DISCOVERED_COUNT_KEY)).isEqualTo(2);
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.UPLOADED_COUNT_KEY)).isEqualTo(1);
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.FAILED_COUNT_KEY)).isEqualTo(1);
    assertThat(Files.exists(failedArchive)).isTrue();
    assertThat(Files.exists(uploadedArchive)).isTrue();
  }

  @Test
  void execute_succeedsWithZeroCountsWhenArchiveDirectoryDoesNotExist() throws Exception {
    ExecutionContext executionContext = new ExecutionContext();

    RepeatStatus result = tasklet(tempDirectory.resolve("missing"))
        .execute(null, chunkContext(executionContext, 2L));

    assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    assertThat(executionContext.getInt(LogArchiveBackupTasklet.DISCOVERED_COUNT_KEY)).isZero();
    verify(logArchiveStorage, never()).upload(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString()
    );
  }

  @Test
  void execute_rejectsWhenOlderBackupJobIsStillRunning() {
    JobExecution olderExecution = new JobExecution(1L);
    given(jobExplorer.findRunningJobExecutions(LogArchiveBackupJobConfig.JOB_NAME))
        .willReturn(Set.of(olderExecution));

    assertThatThrownBy(
        () -> tasklet(tempDirectory).execute(null, chunkContext(new ExecutionContext(), 2L))
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("로그 아카이브 백업 Job이 이미 실행 중입니다.");

    verify(logArchiveStorage, never()).exists(org.mockito.ArgumentMatchers.anyString());
  }

  private LogArchiveBackupTasklet tasklet(Path archiveDirectory) {
    return new LogArchiveBackupTasklet(
        logArchiveStorage,
        jobExplorer,
        archiveDirectory.toString(),
        "logs/mopl",
        "instance-a"
    );
  }

  private ChunkContext chunkContext(ExecutionContext executionContext, Long jobExecutionId) {
    JobExecution jobExecution = new JobExecution(jobExecutionId);
    jobExecution.setExecutionContext(executionContext);
    StepExecution stepExecution = new StepExecution(
        LogArchiveBackupJobConfig.STEP_NAME,
        jobExecution
    );
    return new ChunkContext(new StepContext(stepExecution));
  }
}
