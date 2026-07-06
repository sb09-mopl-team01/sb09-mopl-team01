package io.mopl.domain.contentroomchat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.contentroomchat.mapper.ContentChatMapper;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.service.UserService;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentRoomChatServiceTest {

  @InjectMocks
  private ContentRoomChatService contentRoomChatService;

  @Mock
  private UserService userService;

  @Mock
  private WatchingSessionRepository watchingSessionRepository;

  @Mock
  private ContentChatMapper contentChatMapper;

  private UUID senderId;
  private UUID contentId;
  private UserDto sender;

  @BeforeEach
  void setUp() {
    senderId = UUID.randomUUID();
    contentId = UUID.randomUUID();
    sender = UserDto.builder()
        .id(senderId)
        .email("sender@example.com")
        .name("sender")
        .profileImageUrl(null)
        .build();
  }

  @Test
  void createChatMessageReturnsDtoWhenSenderIsWatchingContent() {
    ContentChatDto expected = new ContentChatDto(null, "hello");

    when(watchingSessionRepository.existsByWatcherIdAndContentId(senderId, contentId))
        .thenReturn(true);
    when(userService.findUser(senderId)).thenReturn(sender);
    when(contentChatMapper.toDto(sender, "hello")).thenReturn(expected);

    ContentChatDto result = contentRoomChatService.createChatMessage(
        senderId,
        contentId,
        " hello "
    );

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void createChatMessageRejectsSenderWhoIsNotWatchingContent() {
    when(watchingSessionRepository.existsByWatcherIdAndContentId(senderId, contentId))
        .thenReturn(false);

    assertThatThrownBy(() -> contentRoomChatService.createChatMessage(senderId, contentId, "hello"))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_CHAT_PARTICIPANT);
  }

  @Test
  void createChatMessageRejectsBlankContent() {
    assertThatThrownBy(() -> contentRoomChatService.createChatMessage(senderId, contentId, " "))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  void createChatMessageRejectsTooLongContent() {
    String tooLongContent = "a".repeat(1001);

    assertThatThrownBy(() -> contentRoomChatService.createChatMessage(
        senderId,
        contentId,
        tooLongContent
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }
}
