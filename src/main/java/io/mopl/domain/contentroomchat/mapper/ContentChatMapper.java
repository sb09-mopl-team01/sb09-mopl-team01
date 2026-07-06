package io.mopl.domain.contentroomchat.mapper;

import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.response.UserSummary;
import org.springframework.stereotype.Component;

@Component
public class ContentChatMapper {

  public ContentChatDto toDto(UserDto sender, String content) {
    return new ContentChatDto(
        UserSummary.builder()
            .userId(sender.id())
            .name(sender.name())
            .profileImageUrl(sender.profileImageUrl())
            .build(),
        content
    );
  }
}
