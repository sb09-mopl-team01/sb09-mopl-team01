package io.mopl.infra.s3;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import io.mopl.global.logging.LogArchiveStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "mopl.logging.backup.enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
public class S3LogArchiveStorage implements LogArchiveStorage {

  private static final String GZIP_CONTENT_TYPE = "application/gzip";

  private final S3Template s3Template;

  @Value("${spring.cloud.aws.s3.bucket}")
  private String bucket;

  @Override
  public boolean exists(String objectKey) {
    return s3Template.objectExists(bucket, normalizeObjectKey(objectKey));
  }

  @Override
  public void upload(Path archive, String objectKey) throws IOException {
    if (archive == null
        || Files.isSymbolicLink(archive)
        || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("로그 백업 대상은 일반 파일이어야 합니다.");
    }

    String normalizedObjectKey = normalizeObjectKey(objectKey);
    ObjectMetadata metadata = ObjectMetadata.builder()
        .contentType(GZIP_CONTENT_TYPE)
        .contentLength(Files.size(archive))
        .build();

    try (InputStream input = Files.newInputStream(archive)) {
      s3Template.upload(bucket, normalizedObjectKey, input, metadata);
    }
  }

  private String normalizeObjectKey(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("S3 로그 백업 object key는 필수입니다.");
    }

    String normalized = objectKey.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    boolean hasUnsafeSegment = java.util.Arrays.stream(normalized.split("/"))
        .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."));
    if (normalized.isBlank() || hasUnsafeSegment) {
      throw new IllegalArgumentException("안전하지 않은 S3 로그 백업 object key입니다.");
    }
    return normalized;
  }
}
