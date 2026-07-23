package io.mopl.domain.playlist.event;

import io.mopl.domain.content.event.ContentSoftDeletedEvent;
import io.mopl.domain.playlist.repository.PlaylistContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentSoftDeletedEventHandler {

  private final PlaylistContentRepository playlistContentRepository;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void handle(ContentSoftDeletedEvent event) {
    int deletedCount = playlistContentRepository.deleteAllByContentId(event.contentId());
    log.info(
        "Playlist content cleanup completed. contentId={}, deletedCount={}",
        event.contentId(),
        deletedCount
    );
  }
}
