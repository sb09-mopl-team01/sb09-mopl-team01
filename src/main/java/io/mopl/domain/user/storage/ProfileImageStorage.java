package io.mopl.domain.user.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ProfileImageStorage {
  String store(MultipartFile file);
  void delete(String fileUrl);
}
