package io.mopl.domain.content.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalContentThumbnailStorageTest {

  @TempDir
  Path tempDirectory;

  @Test
  void deleteRemovesStoredThumbnail() throws Exception {
    Path thumbnail = Files.writeString(tempDirectory.resolve("thumbnail.jpg"), "image");
    LocalContentThumbnailStorage storage = new LocalContentThumbnailStorage(
        tempDirectory.toString(),
        "/content-thumbnails"
    );

    storage.delete("thumbnail.jpg");

    assertThat(thumbnail).doesNotExist();
  }

  @Test
  void deleteRejectsKeyOutsideStorageDirectory() {
    LocalContentThumbnailStorage storage = new LocalContentThumbnailStorage(
        tempDirectory.toString(),
        "/content-thumbnails"
    );

    assertThatThrownBy(() -> storage.delete("../outside.jpg"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("콘텐츠 썸네일 저장 경로를 벗어난 키입니다.");
  }
}
