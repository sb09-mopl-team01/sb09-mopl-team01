package io.mopl.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

  @Mock
  private S3Template s3Template;

  private S3Service s3Service;

  @BeforeEach
  void setUp() {
    s3Service = new S3Service(s3Template);
    ReflectionTestUtils.setField(s3Service, "bucket", "test-bucket");
    ReflectionTestUtils.setField(s3Service, "region", "ap-northeast-2");
  }

  @Test
  void uploadFile_storesFileUnderPrefixAndReturnsPublicUrl() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "thumbnail",
        "poster.PNG",
        "image/png",
        "image".getBytes()
    );

    String url = s3Service.uploadFile(file, "/uploads/contents/thumbnails/");

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(s3Template).upload(
        eq("test-bucket"),
        keyCaptor.capture(),
        any(InputStream.class),
        any(ObjectMetadata.class)
    );
    String key = keyCaptor.getValue();

    assertThat(key).startsWith("uploads/contents/thumbnails/");
    assertThat(key).endsWith(".png");
    assertThat(url).isEqualTo("https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key);
  }

  @Test
  void deleteFile_deletesObjectExtractedFromPublicUrl() {
    String url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/uploads/contents/thumbnails/poster.png";

    s3Service.deleteFile(url);

    verify(s3Template).deleteObject("test-bucket", "uploads/contents/thumbnails/poster.png");
  }

  @Test
  void deleteFile_ignoresUrlFromDifferentBucket() {
    String url = "https://other-bucket.s3.ap-northeast-2.amazonaws.com/uploads/contents/thumbnails/poster.png";

    s3Service.deleteFile(url);

    verify(s3Template, never()).deleteObject(any(String.class), any(String.class));
  }
}
