package io.mopl.domain.user.storage.local;

import io.mopl.domain.user.storage.ProfileImageStorage;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalProfileImageStorage implements ProfileImageStorage {

  @Value("${spring.storage.file.upload-dir}")
  private String uploadDir;

  private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif");

  @Override
  public String store(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BaseException(ErrorCode.PROFILE_IMAGE_INVALID);
    }

    String originalFilename = file.getOriginalFilename();
    String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new BaseException(ErrorCode.PROFILE_IMAGE_INVALID);
    }

    log.debug("ProfileImageStorage Store Started. originalFilename={}", file.getOriginalFilename());
    try {
      Tika tika = new Tika();
      String detectedType = tika.detect(file.getInputStream());

      if (!detectedType.startsWith("image/")) {
        log.warn("[ProfileImageStorage] Malicious file detected. Detected Type: {}", detectedType);
        throw new BaseException(ErrorCode.PROFILE_IMAGE_INVALID);
      }

      String fileName = UUID.randomUUID().toString() + "_" + System.currentTimeMillis() + "." + extension;

      Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
      Path targetLocation = uploadPath.resolve(fileName).normalize();

      if (!targetLocation.startsWith(uploadPath)) {
        throw new SecurityException("Invalid file path");
      }

      if (!Files.exists(uploadPath)) {
        Files.createDirectories(uploadPath);
      }

      file.transferTo(targetLocation);
      log.debug("ProfileImageStorage Store Completed. path={}", targetLocation);

      return "/uploads/images/" + fileName;
    } catch (IOException e) {
      log.error("ProfileImageStorage Store Failed. originalFilename={}", file.getOriginalFilename(), e);
      throw new RuntimeException("파일 저장 실패", e);
    }
  }

  public void delete(String fileUrl) {
    log.debug("ProfileImageStorage Delete Started. url={}", fileUrl);
    try {
      String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
      Path filePath = Paths.get(uploadDir).resolve(fileName).normalize();

      if (filePath.startsWith(Paths.get(uploadDir).toAbsolutePath().normalize()) && Files.exists(filePath)) {
        Files.delete(filePath);
        log.debug("ProfileImageStorage Delete Completed. path={}", filePath);
      } else {
        log.debug("ProfileImageStorage Delete Skipped. File not found or invalid path. path={}", filePath);
      }
    } catch (IOException e) {
      log.error("ProfileImageStorage Delete Failed. url={}", fileUrl, e);
    }
  }
}
