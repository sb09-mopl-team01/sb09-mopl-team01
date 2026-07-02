package io.mopl.domain.directmessage.mapper;

import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ConversationMapper {

  public ConversationDto toDto(Conversation conversation, User withUser) {
    return new ConversationDto(
        conversation.getId(),
        toUserSummary(withUser),
        null,
        false
    );
  }

  private UserSummary toUserSummary(User user) {
    return UserSummary.builder()
        .userId(user.getId())
        .name(user.getName())
        .profileImageUrl(user.getProfileImageUrl())
        .build();
  }
}
