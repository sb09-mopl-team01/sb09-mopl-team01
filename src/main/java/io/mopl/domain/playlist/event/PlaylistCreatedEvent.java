package io.mopl.domain.playlist.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistCreatedEvent(
    UUID playlistId,
    String playlistTitle,
    UUID ownerId,
    String ownerName,
    List<UUID> followerIds,
    Instant occurredAt
) {
}
