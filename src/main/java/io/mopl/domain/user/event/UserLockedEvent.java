package io.mopl.domain.user.event;

import java.util.UUID;

public record UserLockedEvent(
    UUID userId
) {
}
