package io.mopl.domain.playlist.repository;

import java.util.List;
import java.util.UUID;

public interface PlaylistSubscriptionRepositoryCustom {

  List<UUID> findSubscriberIdsByPlaylistId(UUID playlistId);
}
