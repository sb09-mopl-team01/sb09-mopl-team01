package io.mopl.domain.content.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(
    name = "mopl.content.thumbnail.storage-type",
    havingValue = "local",
    matchIfMissing = true
)
@Slf4j
public class LocalContentThumbnailStorage implements ContentThumbnailStorage {

  private final Path storagePath;
  private final String urlPrefix;

  public LocalContentThumbnailStorage(
      @Value("${mopl.content.thumbnail.storage-path:build/content-thumbnails}") String storagePath,
      @Value("${mopl.content.thumbnail.url-prefix:/content-thumbnails}") String urlPrefix
  ) {
    this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
    this.urlPrefix = normalizeUrlPrefix(urlPrefix);
  }

  @Override
  public ContentThumbnailFile upload(MultipartFile thumbnail) {
    try {
      Files.createDirectories(storagePath);
      String extension = StringUtils.getFilenameExtension(thumbnail.getOriginalFilename());
      String storedFileName = UUID.randomUUID()
          + (extension == null || extension.isBlank() ? "" : "." + extension.toLowerCase(Locale.ROOT));
      Path targetPath = storagePath.resolve(storedFileName).normalize();
      thumbnail.transferTo(targetPath);
      String thumbnailUrl = urlPrefix + "/" + storedFileName;
      log.info("Content thumbnail upload completed. storage=local thumbnailKey={}", storedFileName);
      return new ContentThumbnailFile(thumbnailUrl, storedFileName);
    } catch (IOException e) {
      log.error("Content thumbnail upload failed. storage=local originalFilename={}",
          thumbnail == null ? null : thumbnail.getOriginalFilename(), e);
      throw new IllegalStateException("콘텐츠 썸네일 저장에 실패했습니다.", e);
    }
  }

  @Override
  public void delete(String thumbnailKey) {
    if (thumbnailKey == null || thumbnailKey.isBlank()) {
      return;
    }

    Path targetPath = storagePath.resolve(thumbnailKey).normalize();
    if (!targetPath.startsWith(storagePath)) {
      throw new IllegalArgumentException("콘텐츠 썸네일 저장 경로를 벗어난 키입니다.");
    }

    try {
      Files.deleteIfExists(targetPath);
      log.info("Content thumbnail delete completed. storage=local thumbnailKey={}", thumbnailKey);
    } catch (IOException e) {
      throw new IllegalStateException("콘텐츠 썸네일 삭제에 실패했습니다.", e);
    }
  }

  private String normalizeUrlPrefix(String urlPrefix) {
    if (urlPrefix == null || urlPrefix.isBlank()) {
      return "/content-thumbnails";
    }
    String normalized = urlPrefix.trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
