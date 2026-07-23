package io.mopl.domain.content.service;

import io.mopl.domain.content.storage.ContentThumbnailFile;
import io.mopl.domain.content.storage.ContentThumbnailStorage;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnBean(ContentThumbnailStorage.class)
@RequiredArgsConstructor
@Slf4j
public class ContentThumbnailService {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
      "image/jpeg",
      "image/png",
      "image/webp"
  );
  private static final long MAX_THUMBNAIL_SIZE_BYTES = 5 * 1024 * 1024;

  private final ContentThumbnailStorage contentThumbnailStorage;

  public ContentThumbnailFile uploadRequired(MultipartFile thumbnail) {
    validateThumbnail(thumbnail, true);
    return contentThumbnailStorage.upload(thumbnail);
  }

  public ContentThumbnailFile uploadOptional(MultipartFile thumbnail) {
    if (thumbnail == null || thumbnail.isEmpty()) {
      return null;
    }
    validateThumbnail(thumbnail, false);
    return contentThumbnailStorage.upload(thumbnail);
  }

  public void delete(String thumbnailKey) {
    if (thumbnailKey == null || thumbnailKey.isBlank()) {
      return;
    }
    try {
      contentThumbnailStorage.delete(thumbnailKey);
    } catch (RuntimeException e) {
      log.warn("Content thumbnail delete failed. thumbnailKey={}, errorType={}",
          thumbnailKey, e.getClass().getSimpleName(), e);
    }
  }

  private void validateThumbnail(MultipartFile thumbnail, boolean required) {
    if (thumbnail == null || thumbnail.isEmpty()) {
      if (required) {
        throw new IllegalArgumentException("콘텐츠 썸네일은 필수입니다.");
      }
      return;
    }

    String contentType = thumbnail.getContentType();
    if (contentType == null
        || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("지원하지 않는 콘텐츠 썸네일 이미지 형식입니다.");
    }

    if (thumbnail.getSize() > MAX_THUMBNAIL_SIZE_BYTES) {
      throw new IllegalArgumentException("콘텐츠 썸네일 파일 크기는 5MB를 초과할 수 없습니다.");
    }

    String extension = StringUtils.getFilenameExtension(thumbnail.getOriginalFilename());
    if (extension == null || extension.isBlank()) {
      throw new IllegalArgumentException("콘텐츠 썸네일 파일 확장자를 확인할 수 없습니다.");
    }
  }
}
