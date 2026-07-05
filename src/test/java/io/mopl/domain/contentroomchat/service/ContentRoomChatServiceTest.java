package io.mopl.domain.contentroomchat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.contentroomchat.mapper.ContentChatMapper;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentRoomChatServiceTest {

  @InjectMocks
  private ContentRoomChatService contentRoomChatService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private WatchingSessionRepository watchingSessionRepository;

  @Mock
  private ContentChatMapper contentChatMapper;

  private UUID senderId;
  private UUID contentId;
  private User sender;

  @BeforeEach
  void setUp() {
    senderId = UUID.randomUUID();
    contentId = UUID.randomUUID();
    sender = User.builder()
        .email("sender@example.com")
        .passwordHash("hash")
        .name("sender")
        .build();
    ReflectionTestUtils.setField(sender, "id", senderId);
  }

  @Test
  void createChatMessageReturnsDtoWhenSenderIsWatchingContent() {
    ContentChatDto expected = new ContentChatDto(null, "hello");

    when(watchingSessionRepository.existsByWatcherIdAndContentId(senderId, contentId))
        .thenReturn(true);
    when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
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
