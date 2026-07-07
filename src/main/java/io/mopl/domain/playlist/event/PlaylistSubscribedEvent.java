package io.mopl.domain.playlist.event;

import java.time.Instant;
import java.util.UUID;

public record PlaylistSubscribedEvent(
    UUID playlistId,
    String playlistTitle,
    UUID ownerId,
    UUID subscriberId,
    String subscriberName,
    Instant occurredAt
) {
}
