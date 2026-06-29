package io.mopl.domain.content.service;

import io.mopl.domain.content.storage.ContentThumbnailStorage;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnBean(ContentThumbnailStorage.class)
@RequiredArgsConstructor
public class ContentThumbnailService {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
      "image/jpeg",
      "image/png",
      "image/webp"
  );

  private final ContentThumbnailStorage contentThumbnailStorage;

  public String uploadRequired(MultipartFile thumbnail) {
    validateThumbnail(thumbnail, true);
    return contentThumbnailStorage.upload(thumbnail);
  }

  public String uploadOptional(MultipartFile thumbnail, String currentThumbnailUrl) {
    if (thumbnail == null || thumbnail.isEmpty()) {
      return currentThumbnailUrl;
    }
    validateThumbnail(thumbnail, false);
    return contentThumbnailStorage.upload(thumbnail);
  }

  public void delete(String thumbnailUrl) {
    if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
      return;
    }
    contentThumbnailStorage.delete(thumbnailUrl);
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

    String extension = StringUtils.getFilenameExtension(thumbnail.getOriginalFilename());
    if (extension == null || extension.isBlank()) {
      throw new IllegalArgumentException("콘텐츠 썸네일 파일 확장자를 확인할 수 없습니다.");
    }
  }
}
