package io.mopl.domain.watchingsession.event;

import java.time.Instant;
import java.util.UUID;

public record WatchingSessionEnteredEvent(
    UUID sessionId,
    UUID watcherId,
    UUID contentId,
    Instant occurredAt
) {
}
