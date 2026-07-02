package io.mopl.domain.watchingsession.dto;

import java.time.Instant;
import java.util.UUID;

public record WatchingSessionEventMessage(
    String type,
    UUID sessionId,
    UUID watcherId,
    UUID contentId,
    Instant occurredAt
) {
}
