package io.mopl.domain.content.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.mopl.infra.s3.S3Service;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class S3ContentThumbnailStorageTest {

  @Mock
  private S3Service s3Service;

  private S3ContentThumbnailStorage storage;

  @BeforeEach
  void setUp() {
    storage = new S3ContentThumbnailStorage(s3Service);
    ReflectionTestUtils.setField(storage, "keyPrefix", "uploads/contents/thumbnails");
  }

  @Test
  void upload_delegatesToS3ServiceWithContentThumbnailPrefix() throws Exception {
    MockMultipartFile thumbnail = thumbnail();
    String uploadedUrl = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/uploads/contents/thumbnails/poster.png";
    given(s3Service.uploadFile(thumbnail, "uploads/contents/thumbnails")).willReturn(uploadedUrl);

    String result = storage.upload(thumbnail);

    assertThat(result).isEqualTo(uploadedUrl);
    verify(s3Service).uploadFile(thumbnail, "uploads/contents/thumbnails");
  }

  @Test
  void upload_throwsIllegalStateExceptionWhenUploadFails() throws Exception {
    MockMultipartFile thumbnail = thumbnail();
    given(s3Service.uploadFile(thumbnail, "uploads/contents/thumbnails"))
        .willThrow(new IOException("s3 upload failed"));

    assertThatThrownBy(() -> storage.upload(thumbnail))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("콘텐츠 썸네일 저장에 실패했습니다.");
  }

  @Test
  void delete_delegatesToS3Service() {
    String thumbnailUrl = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/uploads/contents/thumbnails/poster.png";

    storage.delete(thumbnailUrl);

    verify(s3Service).deleteFile(thumbnailUrl);
  }

  @Test
  void delete_ignoresBlankUrl() {
    storage.delete(" ");

    verifyNoInteractions(s3Service);
  }

  private MockMultipartFile thumbnail() {
    return new MockMultipartFile(
        "thumbnail",
        "poster.png",
        "image/png",
        "thumbnail".getBytes()
    );
  }
}
