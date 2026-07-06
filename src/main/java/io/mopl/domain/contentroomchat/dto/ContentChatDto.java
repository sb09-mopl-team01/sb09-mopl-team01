package io.mopl.domain.contentroomchat.dto;

import io.mopl.domain.user.dto.response.UserSummary;

public record ContentChatDto(
    UserSummary sender,
    String content
) {
}
