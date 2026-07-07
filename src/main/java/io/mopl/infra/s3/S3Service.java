package io.mopl.infra.s3;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class S3Service {

  private final S3Template s3Template;

  @Value("${spring.cloud.aws.s3.bucket}")
  private String bucket;

  @Value("${spring.cloud.aws.region.static:ap-northeast-2}")
  private String region;

  public String uploadFile(MultipartFile file) throws IOException {
    return uploadFile(file, "");
  }

  public String uploadFile(MultipartFile file, String keyPrefix) throws IOException {
    String key = buildObjectKey(file, keyPrefix);
    ObjectMetadata metadata = ObjectMetadata.builder()
        .contentType(file.getContentType())
        .contentLength(file.getSize())
        .build();

    s3Template.upload(bucket, key, file.getInputStream(), metadata);

    return getPublicUrl(key);
  }

  public void deleteFile(String fileUrl) {
    String key = extractObjectKey(fileUrl);
    if (key == null || key.isBlank()) {
      return;
    }
    s3Template.deleteObject(bucket, key);
  }

  private String buildObjectKey(MultipartFile file, String keyPrefix) {
    String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
    String fileName = UUID.randomUUID()
        + (extension == null || extension.isBlank() ? "" : "." + extension.toLowerCase());
    String normalizedPrefix = normalizeKeyPrefix(keyPrefix);
    return normalizedPrefix.isBlank() ? fileName : normalizedPrefix + "/" + fileName;
  }

  private String normalizeKeyPrefix(String keyPrefix) {
    if (keyPrefix == null || keyPrefix.isBlank()) {
      return "";
    }
    String normalized = keyPrefix.trim().replace("\\", "/");
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private String getPublicUrl(String key) {
    return getPublicUrlPrefix() + key;
  }

  private String getPublicUrlPrefix() {
    return "https://" + bucket + ".s3." + region + ".amazonaws.com/";
  }

  private String extractObjectKey(String fileUrl) {
    if (fileUrl == null || fileUrl.isBlank()) {
      return null;
    }

    String publicUrlPrefix = getPublicUrlPrefix();
    if (fileUrl.startsWith(publicUrlPrefix)) {
      return fileUrl.substring(publicUrlPrefix.length());
    }

    try {
      URI uri = URI.create(fileUrl);
      String expectedHost = bucket + ".s3." + region + ".amazonaws.com";
      if (!expectedHost.equals(uri.getHost())) {
        return null;
      }

      String path = uri.getPath();
      if (path == null || path.isBlank() || "/".equals(path)) {
        return null;
      }
      return path.startsWith("/") ? path.substring(1) : path;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}

