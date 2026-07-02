package io.mopl.domain.follow.dto;

import java.util.UUID;
import lombok.Builder;

@Builder
public record FollowDto(
    UUID id,
    UUID followeeId,
    UUID followerId
) {
}
