package io.mopl.domain.content.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ContentThumbnailStorageTypeValidatorTest {

  @Test
  void afterPropertiesSet_acceptsSupportedStorageType() {
    ContentThumbnailStorageTypeValidator validator = new ContentThumbnailStorageTypeValidator();
    ReflectionTestUtils.setField(validator, "storageType", "s3");

    assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void afterPropertiesSet_rejectsUnsupportedStorageType() {
    ContentThumbnailStorageTypeValidator validator = new ContentThumbnailStorageTypeValidator();
    ReflectionTestUtils.setField(validator, "storageType", "s33");

    assertThatThrownBy(validator::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("지원하지 않는 콘텐츠 썸네일 저장소 타입입니다");
  }
}
