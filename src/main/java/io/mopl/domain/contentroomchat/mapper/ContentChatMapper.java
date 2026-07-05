package io.mopl.domain.contentroomchat.mapper;

import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ContentChatMapper {

  public ContentChatDto toDto(User sender, String content) {
    return new ContentChatDto(
        UserSummary.builder()
            .userId(sender.getId())
            .name(sender.getName())
            .profileImageUrl(sender.getProfileImageUrl())
            .build(),
        content
    );
  }
}
