package io.mopl.domain.watchingsession.dto;

public record WatchingSessionChange(
    WatchingSessionChangeType type,
    WatchingSessionDto watchingSession,
    long watcherCount
) {
}
