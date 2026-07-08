package io.mopl.domain.user.event;

import io.mopl.domain.user.entity.Role;
import java.time.Instant;
import java.util.UUID;

public record UserRoleChangedEvent(
    UUID userId,
    Role role,
    Instant occurredAt
) {
}
