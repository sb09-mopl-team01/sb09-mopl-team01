package io.mopl.domain.directmessage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ConversationDto(
    UUID id,
    @JsonProperty("with")
    UserSummary with,
    DirectMessageDto lastestMessage,
    boolean hasUnread
) {
}
