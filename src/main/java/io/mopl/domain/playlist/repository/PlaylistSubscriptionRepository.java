package io.mopl.domain.playlist.repository;

import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.PlaylistSubscription;
import io.mopl.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistSubscriptionRepository extends JpaRepository<PlaylistSubscription, UUID> {
  void deleteAllByPlaylistId(UUID playlistId);
  boolean existsByPlaylistAndUser(Playlist playlist, User user);
  Optional<PlaylistSubscription> findByPlaylistAndUser(Playlist playlist, User user);

  @Query("SELECT COUNT(ps) > 0 FROM PlaylistSubscription ps WHERE ps.playlist.id = :playlistId AND ps.user.id = :userId")
  boolean existsByPlaylistIdAndUserId(@Param("playlistId") UUID playlistId, @Param("userId") UUID userId);

}
