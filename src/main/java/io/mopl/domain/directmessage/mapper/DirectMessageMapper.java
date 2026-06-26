package io.mopl.domain.directmessage.mapper;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.dto.UserSummary;
import io.mopl.domain.directmessage.entity.DirectMessage;
import org.springframework.stereotype.Component;

@Component
public class DirectMessageMapper {

  public DirectMessageDto toDto(DirectMessage directMessage) {
    return new DirectMessageDto(
        directMessage.getId(),
        directMessage.getConversation().getId(),
        directMessage.getCreatedAt(),
        UserSummary.from(directMessage.getSenderId()),
        UserSummary.from(directMessage.getReceiverId()),
        directMessage.getContent()
    );
  }
}
