package io.mopl.domain.follow.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FollowCreateRequest(
    @NotNull UUID followeeId
) {
}
