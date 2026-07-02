package io.mopl.domain.playlist.repository;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.PlaylistContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {
  void deleteAllByPlaylistId(UUID playlistId);
  boolean existsByPlaylistAndContent(Playlist playlist, Content content);
  Optional<PlaylistContent> findByPlaylistAndContent(Playlist playlist, Content content);
}
