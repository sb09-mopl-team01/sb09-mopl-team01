package io.mopl.domain.directmessage.mapper;

import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.dto.UserSummary;
import io.mopl.domain.directmessage.entity.Conversation;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConversationMapper {

  public ConversationDto toDto(Conversation conversation, UUID requesterId) {
    UUID withUserId = conversation.getOtherParticipantId(requesterId);
    return new ConversationDto(
        conversation.getId(),
        UserSummary.from(withUserId),
        null,
        false
    );
  }
}
