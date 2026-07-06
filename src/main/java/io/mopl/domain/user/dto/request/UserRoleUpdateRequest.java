package io.mopl.domain.user.dto.request;

import io.mopl.domain.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(
    @NotNull
    Role role
) {}
