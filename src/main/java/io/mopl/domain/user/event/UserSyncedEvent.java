package io.mopl.domain.user.event;
import java.time.Instant;
import java.util.UUID;

public record UserSyncedEvent(
    UUID userId,
    String name,
    String email,
    String role,
    boolean isLocked,
    Instant createdAt
) {}
