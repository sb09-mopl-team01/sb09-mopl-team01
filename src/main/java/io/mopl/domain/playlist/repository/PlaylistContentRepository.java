package io.mopl.domain.playlist.repository;

import io.mopl.domain.playlist.entity.PlaylistContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {
  boolean existsByPlaylistIdAndContentId(UUID playlistId, UUID contentId);
  Optional<PlaylistContent> findByPlaylistIdAndContentId(UUID playlistId, UUID contentId);
  void deleteByPlaylistIdAndContentId(UUID playlistId, UUID contentId);
}
