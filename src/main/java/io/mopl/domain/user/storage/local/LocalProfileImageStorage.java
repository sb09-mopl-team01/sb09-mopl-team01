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

  @Value("${file.upload-dir}")
  private String uploadDir;

  @Override
  public String store(MultipartFile file) {
    try {
      String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
      Path uploadPath = Paths.get(uploadDir);

      if (!Files.exists(uploadPath)) {
        Files.createDirectories(uploadPath);
      }

      file.transferTo(uploadPath.resolve(fileName));
      log.info("[파일 저장] 로컬 저장 완료. path={}", uploadPath.resolve(fileName));

      return "/uploads/images/" + fileName;
    } catch (IOException e) {
      throw new RuntimeException("파일 저장 실패", e);
    }
  }

  @Override
  public void delete(String fileUrl) {
    try {
      String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
      Path filePath = Paths.get(uploadDir).resolve(fileName);

      if (Files.exists(filePath)) {
        Files.delete(filePath);
        log.info("[파일 삭제] 로컬 삭제 완료. path={}", filePath);
      }
    } catch (IOException e) {
      log.error("[파일 삭제] 파일 삭제 실패. url={}", fileUrl, e);
    }
  }
}
