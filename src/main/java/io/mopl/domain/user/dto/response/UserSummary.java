package io.mopl.domain.user.dto.response;

import java.util.UUID;
import lombok.Builder;

@Builder
public record UserSummary(
    UUID userId,
    String name,
    String profileImageUrl
) {}
