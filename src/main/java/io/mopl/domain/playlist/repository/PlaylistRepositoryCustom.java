package io.mopl.domain.playlist.repository;

import io.mopl.domain.playlist.entity.Playlist;
import java.util.List;
import java.util.UUID;

public interface PlaylistRepositoryCustom {
  List<Playlist> findPlaylistsByCursor(
      String keywordLike,
      UUID ownerIdEqual,
      UUID subscriberIdEqual,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      String sortDirection
  );

  long countPlaylists(String keywordLike, UUID ownerIdEqual, UUID subscriberIdEqual);
}