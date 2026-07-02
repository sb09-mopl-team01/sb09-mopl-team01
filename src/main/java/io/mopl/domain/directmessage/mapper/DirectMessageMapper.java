package io.mopl.domain.directmessage.mapper;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.entity.DirectMessage;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
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

  public DirectMessageDto toDto(DirectMessage directMessage, User sender, User receiver) {
    return new DirectMessageDto(
        directMessage.getId(),
        directMessage.getConversation().getId(),
        directMessage.getCreatedAt(),
        toUserSummary(sender),
        toUserSummary(receiver),
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

  private UserSummary toUserSummary(User user) {
    return UserSummary.builder()
        .userId(user.getId())
        .name(user.getName())
        .profileImageUrl(user.getProfileImageUrl())
        .build();
  }
}
