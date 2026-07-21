package io.mopl.domain.user.storage.s3;

import io.awspring.cloud.s3.S3Template;
import io.mopl.domain.user.storage.ProfileImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.storage.type", havingValue = "s3")
public class S3ProfileImageStorage implements ProfileImageStorage {

  private final S3Template s3Template;

  @Value("${spring.cloud.aws.s3.bucket}")
  private String bucket;

  @Value("${spring.cloud.aws.region.static}")
  private String region;

  @Value("${spring.storage.file.upload-dir}")
  private String uploadDir;

  @Override
  public String store(MultipartFile file) {
    try {
      String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

      String cleanDir = uploadDir.startsWith("/") ? uploadDir.substring(1) : uploadDir;
      if (!cleanDir.isEmpty() && !cleanDir.endsWith("/")) {
        cleanDir += "/";
      }
      String s3Key = cleanDir + fileName;

      s3Template.upload(bucket, s3Key, file.getInputStream());

      return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + s3Key;

    } catch (IOException e) {
      log.error("Error occurred while uploading file to S3: {}", e.getMessage());
      throw new RuntimeException("Failed to upload profile image.", e);
    }
  }

  @Override
  public void delete(String fileUrl) {
    if (fileUrl == null || fileUrl.isBlank() || !fileUrl.contains(".amazonaws.com/")) {
      return;
    }

    try {
      String s3Key = fileUrl.substring(fileUrl.indexOf(".amazonaws.com/") + 15);

      s3Template.deleteObject(bucket, s3Key);
      log.info("Successfully deleted image from S3: {}", s3Key);
    } catch (Exception e) {
      log.error("Error occurred while deleting file from S3: URL={}, msg={}", fileUrl, e.getMessage());
    }
  }
}
