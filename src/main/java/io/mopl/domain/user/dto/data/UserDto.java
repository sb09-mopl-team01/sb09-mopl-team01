package io.mopl.domain.user.dto.data;

import io.mopl.domain.user.entity.Role;
import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String name,
    String profileImageUrl,
    Role role,
    boolean locked,
    Instant createdAt,
    Instant updatedAt
) {}