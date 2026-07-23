package io.mopl.domain.playlist.repository;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.PlaylistContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {
  void deleteAllByPlaylistId(UUID playlistId);

  @Modifying(flushAutomatically = true)
  @Query("DELETE FROM PlaylistContent pc WHERE pc.content.id = :contentId")
  int deleteAllByContentId(@Param("contentId") UUID contentId);

  boolean existsByPlaylistAndContent(Playlist playlist, Content content);
  Optional<PlaylistContent> findByPlaylistAndContent(Playlist playlist, Content content);
}
