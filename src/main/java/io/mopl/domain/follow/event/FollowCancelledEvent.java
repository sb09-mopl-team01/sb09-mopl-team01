package io.mopl.domain.follow.event;

import java.time.Instant;
import java.util.UUID;

public record FollowCancelledEvent(
    UUID followId,
    UUID followerId,
    UUID followeeId,
    Instant occurredAt
) {
}
