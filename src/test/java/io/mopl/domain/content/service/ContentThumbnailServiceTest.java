package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.mopl.domain.content.storage.ContentThumbnailStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentThumbnailServiceTest {

  @Mock
  private ContentThumbnailStorage contentThumbnailStorage;

  private ContentThumbnailService contentThumbnailService;

  @BeforeEach
  void setUp() {
    contentThumbnailService = new ContentThumbnailService(contentThumbnailStorage);
  }

  @Test
  void deleteDelegatesToStorage() {
    contentThumbnailService.delete("thumbnail.jpg");

    verify(contentThumbnailStorage).delete("thumbnail.jpg");
  }

  @Test
  void deleteIgnoresBlankKey() {
    contentThumbnailService.delete(" ");

    verifyNoInteractions(contentThumbnailStorage);
  }

  @Test
  void deleteDoesNotFailBusinessFlowWhenStorageDeletionFails() {
    doThrow(new IllegalStateException("storage failed"))
        .when(contentThumbnailStorage).delete("thumbnail.jpg");

    assertThatCode(() -> contentThumbnailService.delete("thumbnail.jpg"))
        .doesNotThrowAnyException();

    verify(contentThumbnailStorage).delete("thumbnail.jpg");
  }
}
