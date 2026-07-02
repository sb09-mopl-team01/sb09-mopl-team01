package io.mopl.domain.directmessage.dto;

import io.mopl.domain.user.dto.response.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record DirectMessageDto(
    UUID id,
    UUID conversationId,
    Instant createdAt,
    UserSummary sender,
    UserSummary receiver,
    String content
) {
}
