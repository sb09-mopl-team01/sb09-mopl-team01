package io.mopl.domain.content.storage;

import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContentThumbnailStorageTypeValidator implements InitializingBean {

  private static final Set<String> SUPPORTED_STORAGE_TYPES = Set.of("local", "s3");

  @Value("${mopl.content.thumbnail.storage-type:local}")
  private String storageType;

  @Override
  public void afterPropertiesSet() {
    if (!SUPPORTED_STORAGE_TYPES.contains(storageType)) {
      throw new IllegalStateException(
          "지원하지 않는 콘텐츠 썸네일 저장소 타입입니다: " + storageType
              + " (지원값: local, s3)"
      );
    }
  }
}
