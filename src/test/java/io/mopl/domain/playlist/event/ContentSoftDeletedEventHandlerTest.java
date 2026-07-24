package io.mopl.domain.playlist.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.event.ContentSoftDeletedEvent;
import io.mopl.domain.playlist.repository.PlaylistContentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class ContentSoftDeletedEventHandlerTest {

  @Mock
  private PlaylistContentRepository playlistContentRepository;

  @Test
  void contentSoftDeleteRemovesPlaylistContentLinks() {
    UUID contentId = UUID.randomUUID();
    given(playlistContentRepository.deleteAllByContentId(contentId)).willReturn(2);
    ContentSoftDeletedEventHandler handler = new ContentSoftDeletedEventHandler(
        playlistContentRepository
    );

    handler.handle(new ContentSoftDeletedEvent(contentId));

    verify(playlistContentRepository).deleteAllByContentId(contentId);
  }

  @Test
  void contentSoftDeleteIsHandledBeforeCommit() throws Exception {
    TransactionalEventListener annotation = ContentSoftDeletedEventHandler.class
        .getMethod("handle", ContentSoftDeletedEvent.class)
        .getAnnotation(TransactionalEventListener.class);

    assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
    assertThat(annotation.fallbackExecution()).isFalse();
  }
}
