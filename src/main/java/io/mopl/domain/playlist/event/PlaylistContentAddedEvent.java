package io.mopl.domain.playlist.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistContentAddedEvent(
    UUID playlistId,
    String playlistTitle,
    UUID contentId,
    String contentTitle,
    List<UUID> subscriberIds,
    Instant occurredAt
) {
}
