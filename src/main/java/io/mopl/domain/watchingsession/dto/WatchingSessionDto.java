package io.mopl.domain.watchingsession.dto;

import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.user.dto.response.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record WatchingSessionDto(
    UUID id,
    Instant createdAt,
    UserSummary watcher,
    ContentSummary content
) {
}
