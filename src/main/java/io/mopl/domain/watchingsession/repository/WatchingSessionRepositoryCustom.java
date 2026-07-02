package io.mopl.domain.watchingsession.repository;

import io.mopl.domain.watchingsession.entity.WatchingSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface WatchingSessionRepositoryCustom {

  List<WatchingSession> findByContentIdWithCursorDesc(
      UUID contentId,
      String watcherNameLike,
      Instant cursor,
      UUID idAfter,
      Pageable pageable
  );

  List<WatchingSession> findByContentIdWithCursorAsc(
      UUID contentId,
      String watcherNameLike,
      Instant cursor,
      UUID idAfter,
      Pageable pageable
  );

  long countByContentId(UUID contentId, String watcherNameLike);
}
