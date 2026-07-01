package io.mopl.domain.directmessage.mapper;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.entity.DirectMessage;
import io.mopl.domain.user.dto.response.UserSummary;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DirectMessageMapper {

  public DirectMessageDto toDto(DirectMessage directMessage) {
    return new DirectMessageDto(
        directMessage.getId(),
        directMessage.getConversation().getId(),
        directMessage.getCreatedAt(),
        toUserSummary(directMessage.getSenderId()),
        toUserSummary(directMessage.getReceiverId()),
        directMessage.getContent()
    );
  }

  private UserSummary toUserSummary(UUID userId) {
    return UserSummary.builder()
        .userId(userId)
        .name(userId.toString())
        .profileImageUrl(null)
        .build();
  }
}
