package io.mopl.domain.contentroomchat.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.contentroomchat.dto.ContentChatSendRequest;
import io.mopl.domain.contentroomchat.service.ContentRoomChatService;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class ContentRoomChatWebSocketControllerTest {

  private final ContentRoomChatService contentRoomChatService = mock(ContentRoomChatService.class);
  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final ContentRoomChatWebSocketController controller =
      new ContentRoomChatWebSocketController(contentRoomChatService, messagingTemplate);

  private UUID senderId;
  private UUID contentId;
  private Principal principal;

  @BeforeEach
  void setUp() {
    senderId = UUID.randomUUID();
    contentId = UUID.randomUUID();
    User sender = User.builder()
        .email("sender@example.com")
        .passwordHash("hash")
        .name("sender")
        .build();
    ReflectionTestUtils.setField(sender, "id", senderId);
    MoplUserDetails userDetails = new MoplUserDetails(sender);
    principal = new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );
  }

  @Test
  void sendMessageBroadcastsContentChatDtoToContentChatTopic() {
    ContentChatSendRequest request = new ContentChatSendRequest("hello");
    ContentChatDto response = new ContentChatDto(
        UserSummary.builder()
            .userId(senderId)
            .name("sender")
            .profileImageUrl(null)
            .build(),
        "hello"
    );

    when(contentRoomChatService.createChatMessage(senderId, contentId, "hello"))
        .thenReturn(response);

    controller.sendMessage(contentId, request, principal);

    verify(contentRoomChatService).createChatMessage(senderId, contentId, "hello");
    verify(messagingTemplate).convertAndSend(
        "/sub/contents/%s/chat".formatted(contentId),
        response
    );
  }

  @Test
  void sendMessageRejectsNullPayload() {
    assertThatThrownBy(() -> controller.sendMessage(contentId, null, principal))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  void sendMessageRequiresAuthenticationPrincipal() {
    ContentChatSendRequest request = new ContentChatSendRequest("hello");

    assertThatThrownBy(() -> controller.sendMessage(contentId, request, () -> "anonymous"))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
  }

  @Test
  void sendMessageRejectsUserDetailsWithoutUser() {
    ContentChatSendRequest request = new ContentChatSendRequest("hello");
    Principal invalidPrincipal = new UsernamePasswordAuthenticationToken(
        new MoplUserDetails(null),
        null
    );

    assertThatThrownBy(() -> controller.sendMessage(contentId, request, invalidPrincipal))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
  }
}
