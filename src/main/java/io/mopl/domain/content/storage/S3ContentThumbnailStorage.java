package io.mopl.domain.content.storage;

import io.mopl.infra.s3.S3Service;
import io.mopl.infra.s3.S3Service.S3StoredFile;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(
    name = "mopl.content.thumbnail.storage-type",
    havingValue = "s3"
)
@RequiredArgsConstructor
@Slf4j
public class S3ContentThumbnailStorage implements ContentThumbnailStorage {

  private final S3Service s3Service;

  @Value("${mopl.content.thumbnail.s3.key-prefix:uploads/contents/thumbnails}")
  private String keyPrefix;

  @Override
  public ContentThumbnailFile upload(MultipartFile thumbnail) {
    try {
      S3StoredFile storedFile = s3Service.uploadFileWithKey(thumbnail, keyPrefix);
      log.info("Content thumbnail upload completed. storage=s3 thumbnailKey={}", storedFile.key());
      return new ContentThumbnailFile(storedFile.url(), storedFile.key());
    } catch (IOException | RuntimeException e) {
      log.error("Content thumbnail upload failed. storage=s3 originalFilename={}",
          thumbnail == null ? null : thumbnail.getOriginalFilename(), e);
      throw new IllegalStateException("콘텐츠 썸네일 저장에 실패했습니다.", e);
    }
  }

  @Override
  public void delete(String thumbnailKey) {
    if (thumbnailKey == null || thumbnailKey.isBlank()) {
      return;
    }

    try {
      s3Service.deleteFileByKey(thumbnailKey);
      log.info("Content thumbnail delete completed. storage=s3 thumbnailKey={}", thumbnailKey);
    } catch (RuntimeException e) {
      log.warn("Content thumbnail delete failed. storage=s3 thumbnailKey={}", thumbnailKey, e);
    }
  }
}
