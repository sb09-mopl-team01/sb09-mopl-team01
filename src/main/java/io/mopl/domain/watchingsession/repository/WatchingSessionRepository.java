package io.mopl.domain.watchingsession.repository;

import io.mopl.domain.watchingsession.entity.WatchingSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchingSessionRepository extends
    JpaRepository<WatchingSession, UUID>,
    WatchingSessionRepositoryCustom {

  boolean existsByWatcherId(UUID watcherId);

  boolean existsByWatcherIdAndContentId(UUID watcherId, UUID contentId);

  Optional<WatchingSession> findByWatcherId(UUID watcherId);

  void deleteByWatcherId(UUID watcherId);
}
