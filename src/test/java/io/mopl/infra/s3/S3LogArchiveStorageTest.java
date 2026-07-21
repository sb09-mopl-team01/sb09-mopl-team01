package io.mopl.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class S3LogArchiveStorageTest {

  @Mock
  private S3Template s3Template;

  @TempDir
  private Path tempDirectory;

  private S3LogArchiveStorage storage;

  @BeforeEach
  void setUp() {
    storage = new S3LogArchiveStorage(s3Template);
    ReflectionTestUtils.setField(storage, "bucket", "test-bucket");
  }

  @Test
  void exists_checksNormalizedObjectKey() {
    given(s3Template.objectExists("test-bucket", "logs/mopl/archive.log.gz"))
        .willReturn(true);

    boolean exists = storage.exists("/logs/mopl/archive.log.gz");

    assertThat(exists).isTrue();
  }

  @Test
  void upload_storesGzipArchiveWithMetadata() throws Exception {
    Path archive = Files.writeString(tempDirectory.resolve("archive.log.gz"), "archive");

    storage.upload(archive, "logs/mopl/archive.log.gz");

    ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
    verify(s3Template).upload(
        eq("test-bucket"),
        eq("logs/mopl/archive.log.gz"),
        any(InputStream.class),
        metadataCaptor.capture()
    );
    assertThat(metadataCaptor.getValue().getContentType()).isEqualTo("application/gzip");
    assertThat(metadataCaptor.getValue().getContentLength()).isEqualTo(Files.size(archive));
  }

  @Test
  void upload_rejectsMissingArchive() {
    assertThatThrownBy(
        () -> storage.upload(tempDirectory.resolve("missing.log.gz"), "logs/mopl/missing.log.gz")
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("로그 백업 대상은 일반 파일이어야 합니다.");
  }

  @Test
  void exists_rejectsUnsafeObjectKey() {
    assertThatThrownBy(() -> storage.exists("../secret.log.gz"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("안전하지 않은 S3 로그 백업 object key입니다.");
  }
}
