package io.mopl.domain.user.storage.local;

import io.mopl.domain.user.storage.ProfileImageStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Component
public class LocalProfileImageStorage implements ProfileImageStorage {

  @Value("${spring.file.upload-dir}")
  private String uploadDir;

  @Override
  public String store(MultipartFile file) {
    log.debug("ProfileImageStorage Store Started. originalFilename={}", file.getOriginalFilename());
    try {
      String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
      Path uploadPath = Paths.get(uploadDir);

      if (!Files.exists(uploadPath)) {
        Files.createDirectories(uploadPath);
      }

      file.transferTo(uploadPath.resolve(fileName));
      log.debug("ProfileImageStorage Store Completed. path={}", uploadPath.resolve(fileName));

      return uploadDir + fileName;
    } catch (IOException e) {
      log.error("ProfileImageStorage Store Failed. originalFilename={}", file.getOriginalFilename(), e);
      throw new RuntimeException("파일 저장 실패", e);
    }
  }

  @Override
  public void delete(String fileUrl) {
    log.debug("ProfileImageStorage Delete Started. url={}", fileUrl);
    try {
      String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
      Path filePath = Paths.get(uploadDir).resolve(fileName);

      if (Files.exists(filePath)) {
        Files.delete(filePath);
        log.debug("ProfileImageStorage Delete Completed. path={}", filePath);
      } else {
        log.debug("ProfileImageStorage Delete Skipped. File not found. path={}", filePath);
      }
    } catch (IOException e) {
      log.error("ProfileImageStorage Delete Failed. url={}", fileUrl, e);
    }
  }
}
