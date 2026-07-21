package io.mopl.domain.watchingsession.event;

import io.mopl.domain.watchingsession.dto.WatchingSessionDto;
import java.time.Instant;

public record WatchingSessionLeftEvent(
    WatchingSessionDto watchingSession,
    long watcherCount,
    Instant occurredAt
) {
}
