package io.mopl.domain.content.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
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
  public String upload(MultipartFile thumbnail) {
    try {
      Files.createDirectories(storagePath);
      String extension = StringUtils.getFilenameExtension(thumbnail.getOriginalFilename());
      String storedFileName = UUID.randomUUID()
          + (extension == null || extension.isBlank() ? "" : "." + extension.toLowerCase());
      Path targetPath = storagePath.resolve(storedFileName).normalize();
      thumbnail.transferTo(targetPath);
      return urlPrefix + "/" + storedFileName;
    } catch (IOException e) {
      log.error("Content thumbnail upload failed. originalFilename={}",
          thumbnail == null ? null : thumbnail.getOriginalFilename(), e);
      throw new IllegalStateException("콘텐츠 썸네일 저장에 실패했습니다.", e);
    }
  }

  @Override
  public void delete(String thumbnailUrl) {
    if (thumbnailUrl == null || thumbnailUrl.isBlank()
        || !thumbnailUrl.startsWith(urlPrefix + "/")) {
      return;
    }

    String storedFileName = thumbnailUrl.substring((urlPrefix + "/").length());
    Path targetPath = storagePath.resolve(storedFileName).normalize();
    if (!targetPath.startsWith(storagePath)) {
      log.warn("Content thumbnail delete rejected. thumbnailUrl={}", thumbnailUrl);
      return;
    }

    try {
      Files.deleteIfExists(targetPath);
    } catch (IOException e) {
      log.warn("Content thumbnail delete failed. thumbnailUrl={}", thumbnailUrl, e);
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
