package io.mopl.global.logging;

import io.mopl.global.scheduler.LogArchiveBackupJobConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
    name = "mopl.logging.backup.enabled",
    havingValue = "true"
)
public class LogArchiveBackupTasklet implements Tasklet {

  static final String DISCOVERED_COUNT_KEY = "logArchiveBackup.discoveredCount";
  static final String UPLOADED_COUNT_KEY = "logArchiveBackup.uploadedCount";
  static final String SKIPPED_COUNT_KEY = "logArchiveBackup.skippedCount";
  static final String FAILED_COUNT_KEY = "logArchiveBackup.failedCount";

  private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
  private static final Pattern SAFE_SEGMENT_PATTERN = Pattern.compile("[^A-Za-z0-9._-]");

  private final LogArchiveStorage logArchiveStorage;
  private final JobExplorer jobExplorer;
  private final Path archiveDirectory;
  private final String s3KeyPrefix;
  private final String instanceId;

  public LogArchiveBackupTasklet(
      LogArchiveStorage logArchiveStorage,
      JobExplorer jobExplorer,
      @Value("${mopl.logging.backup.archive-directory:logs/archive}") String archiveDirectory,
      @Value("${mopl.logging.backup.s3-key-prefix:logs/mopl}") String s3KeyPrefix,
      @Value("${mopl.logging.backup.instance-id:local}") String instanceId
  ) {
    this.logArchiveStorage = logArchiveStorage;
    this.jobExplorer = jobExplorer;
    this.archiveDirectory = normalizeArchiveDirectory(archiveDirectory);
    this.s3KeyPrefix = normalizeKeyPrefix(s3KeyPrefix);
    this.instanceId = normalizeInstanceId(instanceId);
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    ensureNoOlderRunningExecution(chunkContext);
    List<Path> archives = findCompletedArchives();

    int uploadedCount = 0;
    int skippedCount = 0;
    int failedCount = 0;

    log.info(
        "Log archiveBackup started. discoveredCount={}, instanceId={}",
        archives.size(),
        instanceId
    );

    for (Path archive : archives) {
      String objectKey = buildObjectKey(archive);
      try {
        if (logArchiveStorage.exists(objectKey)) {
          skippedCount++;
          log.debug(
              "Log archiveBackup skipped. reason=alreadyExists, fileName={}, objectKey={}",
              archive.getFileName(),
              objectKey
          );
          continue;
        }

        logArchiveStorage.upload(archive, objectKey);
        uploadedCount++;
        log.debug(
            "Log archiveBackup uploaded. fileName={}, objectKey={}",
            archive.getFileName(),
            objectKey
        );
      } catch (IOException | RuntimeException e) {
        failedCount++;
        log.error(
            "Log archiveBackup failed. fileName={}, objectKey={}, errorType={}, message={}",
            archive.getFileName(),
            objectKey,
            e.getClass().getSimpleName(),
            e.getMessage(),
            e
        );
      }
    }

    putResultToExecutionContext(
        chunkContext,
        archives.size(),
        uploadedCount,
        skippedCount,
        failedCount
    );

    if (failedCount > 0) {
      log.error(
          "Log archiveBackup failed. discoveredCount={}, uploadedCount={}, skippedCount={}, failedCount={}",
          archives.size(),
          uploadedCount,
          skippedCount,
          failedCount
      );
      throw new IllegalStateException("로그 아카이브 백업 중 실패한 파일이 있습니다.");
    }

    log.info(
        "Log archiveBackup completed. discoveredCount={}, uploadedCount={}, skippedCount={}, failedCount={}",
        archives.size(),
        uploadedCount,
        skippedCount,
        failedCount
    );
    return RepeatStatus.FINISHED;
  }

  private List<Path> findCompletedArchives() {
    if (Files.notExists(archiveDirectory)) {
      return List.of();
    }
    if (!Files.isDirectory(archiveDirectory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException("로그 아카이브 경로가 디렉터리가 아닙니다.");
    }

    try (Stream<Path> paths = Files.list(archiveDirectory)) {
      return paths
          .filter(path -> !Files.isSymbolicLink(path))
          .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          .filter(path -> path.getFileName().toString().endsWith(".log.gz"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("로그 아카이브 목록을 읽을 수 없습니다.", e);
    }
  }

  private String buildObjectKey(Path archive) {
    String fileName = archive.getFileName().toString();
    Matcher matcher = DATE_PATTERN.matcher(fileName);
    String datePath = matcher.find()
        ? matcher.group(1) + "/" + matcher.group(2) + "/" + matcher.group(3)
        : "undated";
    return s3KeyPrefix + "/" + instanceId + "/" + datePath + "/" + fileName;
  }

  private void ensureNoOlderRunningExecution(ChunkContext chunkContext) {
    JobExecution currentExecution = chunkContext.getStepContext()
        .getStepExecution()
        .getJobExecution();
    Long currentExecutionId = currentExecution.getId();
    boolean olderExecutionExists = jobExplorer
        .findRunningJobExecutions(LogArchiveBackupJobConfig.JOB_NAME)
        .stream()
        .map(JobExecution::getId)
        .filter(executionId -> executionId != null && currentExecutionId != null)
        .anyMatch(executionId -> executionId < currentExecutionId);

    if (olderExecutionExists) {
      log.warn(
          "Log archiveBackup rejected. reason=alreadyRunning, jobExecutionId={}",
          currentExecutionId
      );
      throw new IllegalStateException("로그 아카이브 백업 Job이 이미 실행 중입니다.");
    }
  }

  private void putResultToExecutionContext(
      ChunkContext chunkContext,
      int discoveredCount,
      int uploadedCount,
      int skippedCount,
      int failedCount
  ) {
    ExecutionContext executionContext = chunkContext.getStepContext()
        .getStepExecution()
        .getJobExecution()
        .getExecutionContext();
    executionContext.putInt(DISCOVERED_COUNT_KEY, discoveredCount);
    executionContext.putInt(UPLOADED_COUNT_KEY, uploadedCount);
    executionContext.putInt(SKIPPED_COUNT_KEY, skippedCount);
    executionContext.putInt(FAILED_COUNT_KEY, failedCount);
  }

  private static Path normalizeArchiveDirectory(String archiveDirectory) {
    if (archiveDirectory == null || archiveDirectory.isBlank()) {
      throw new IllegalArgumentException("로그 아카이브 경로는 필수입니다.");
    }
    return Path.of(archiveDirectory.trim()).toAbsolutePath().normalize();
  }

  private static String normalizeKeyPrefix(String keyPrefix) {
    if (keyPrefix == null || keyPrefix.isBlank()) {
      throw new IllegalArgumentException("S3 로그 백업 key prefix는 필수입니다.");
    }
    String normalized = keyPrefix.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    boolean hasUnsafeSegment = java.util.Arrays.stream(normalized.split("/"))
        .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."));
    if (normalized.isBlank() || hasUnsafeSegment) {
      throw new IllegalArgumentException("안전하지 않은 S3 로그 백업 key prefix입니다.");
    }
    return normalized;
  }

  private static String normalizeInstanceId(String instanceId) {
    if (instanceId == null || instanceId.isBlank()) {
      return "local";
    }
    String normalized = SAFE_SEGMENT_PATTERN.matcher(instanceId.trim()).replaceAll("_");
    return normalized.isBlank() ? "local" : normalized;
  }
}
