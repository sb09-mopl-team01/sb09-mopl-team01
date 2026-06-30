package io.mopl.domain.playlist.repository;

import io.mopl.domain.playlist.entity.PlaylistSubscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistSubscriptionRepository extends JpaRepository<PlaylistSubscription, UUID> {
  boolean existsByPlaylistIdAndUserId(UUID playlistId, UUID userId);
  Optional<PlaylistSubscription> findByPlaylistIdAndUserId(UUID playlistId, UUID userId);
  void deleteByPlaylistIdAndUserId(UUID playlistId, UUID userId);
}
