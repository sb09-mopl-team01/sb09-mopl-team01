package io.mopl.domain.content.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ContentThumbnailStorage {

  ContentThumbnailFile upload(MultipartFile thumbnail);

  void delete(String thumbnailKey);
}
