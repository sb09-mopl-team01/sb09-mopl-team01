package io.mopl.domain.directmessage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.mopl.domain.user.dto.response.UserSummary;
import java.util.UUID;

public record ConversationDto(
    UUID id,
    @JsonProperty("with")
    UserSummary with,
    DirectMessageDto lastestMessage,
    @JsonProperty("lastestMessage")
    DirectMessageDto latestMessage,
    boolean hasUnread
) {
}
