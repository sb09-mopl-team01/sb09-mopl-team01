package io.mopl.domain.user.dto.request;

import io.mopl.domain.user.entity.Role;

public record UserRoleUpdateRequest(
    Role role
) {}
