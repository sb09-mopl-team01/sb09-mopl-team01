package io.mopl.domain.contentroomchat.service;

import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.contentroomchat.mapper.ContentChatMapper;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.service.UserService;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ContentRoomChatService {

  private final UserService userService;
  private final WatchingSessionRepository watchingSessionRepository;
  private final ContentChatMapper contentChatMapper;

  @Transactional(readOnly = true)
  public ContentChatDto createChatMessage(UUID senderId, UUID contentId, String content) {
    String normalizedContent = validateSendCommand(senderId, contentId, content);
    validateParticipant(senderId, contentId);

    UserDto sender = userService.findUser(senderId);

    return contentChatMapper.toDto(sender, normalizedContent);
  }

  private String validateSendCommand(UUID senderId, UUID contentId, String content) {
    if (senderId == null || contentId == null || !StringUtils.hasText(content)) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    String trimmedContent = content.trim();
    if (trimmedContent.length() > 1000) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    return trimmedContent;
  }

  private void validateParticipant(UUID userId, UUID contentId) {
    if (!watchingSessionRepository.existsByWatcherIdAndContentId(userId, contentId)) {
      throw new BaseException(ErrorCode.NOT_CHAT_PARTICIPANT);
    }
  }
}
