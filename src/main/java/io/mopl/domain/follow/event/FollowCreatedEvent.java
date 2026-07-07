package io.mopl.domain.follow.event;

import java.time.Instant;
import java.util.UUID;

public record FollowCreatedEvent(
    UUID followId,
    UUID followerId,
    String followerName,
    UUID followeeId,
    Instant occurredAt
) {
}
