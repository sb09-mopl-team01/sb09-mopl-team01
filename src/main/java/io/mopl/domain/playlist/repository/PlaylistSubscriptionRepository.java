package io.mopl.domain.playlist.repository;

import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.PlaylistSubscription;
import io.mopl.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistSubscriptionRepository extends JpaRepository<PlaylistSubscription, UUID> {
   void deleteAllByPlaylistId(UUID playlistId);
  boolean existsByPlaylistAndUser(Playlist playlist, User user);
  Optional<PlaylistSubscription> findByPlaylistAndUser(Playlist playlist, User user);
}
