package io.mopl.domain.playlist.repository;

import io.mopl.domain.playlist.entity.Playlist;
import java.util.List;
import java.util.UUID;

public interface PlaylistRepositoryCustom {

  List<Playlist> findPlaylistsByCursor(
      String keyword,
      UUID ownerId,
      UUID subscriberId,
      String cursor,
      UUID idAfter,
      int limit,
      String sortDirection,
      String sortBy
  );

  long countPlaylists(String keyword, UUID ownerId, UUID subscriberId);
}