package io.mopl.domain.content.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ContentThumbnailStorage {

  String upload(MultipartFile thumbnail);

  void delete(String thumbnailUrl);
}
