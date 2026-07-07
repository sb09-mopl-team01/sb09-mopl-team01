package io.mopl.domain.content.storage;

import io.mopl.infra.s3.S3Service;
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
  public String upload(MultipartFile thumbnail) {
    try {
      return s3Service.uploadFile(thumbnail, keyPrefix);
    } catch (IOException | RuntimeException e) {
      log.error("Content thumbnail upload failed. originalFilename={}",
          thumbnail == null ? null : thumbnail.getOriginalFilename(), e);
      throw new IllegalStateException("콘텐츠 썸네일 저장에 실패했습니다.", e);
    }
  }

  @Override
  public void delete(String thumbnailUrl) {
    if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
      return;
    }

    try {
      s3Service.deleteFile(thumbnailUrl);
    } catch (RuntimeException e) {
      log.warn("Content thumbnail delete failed. thumbnailUrl={}", thumbnailUrl, e);
    }
  }
}
