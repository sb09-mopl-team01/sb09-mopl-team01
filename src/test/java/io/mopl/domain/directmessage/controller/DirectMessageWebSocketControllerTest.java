package io.mopl.domain.directmessage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.directmessage.dto.DirectMessageSendRequest;
import io.mopl.domain.directmessage.service.ConversationService;
import io.mopl.domain.user.entity.User;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class DirectMessageWebSocketControllerTest {

  private final ConversationService conversationService = mock(ConversationService.class);
  private final DirectMessageWebSocketController controller =
      new DirectMessageWebSocketController(conversationService);

  private UUID senderId;
  private UUID conversationId;
  private Principal principal;

  @BeforeEach
  void setUp() {
    senderId = UUID.randomUUID();
    conversationId = UUID.randomUUID();
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
  void sendDirectMessageDelegatesMessageSavingToService() {
    DirectMessageSendRequest request = new DirectMessageSendRequest("hello");

    controller.sendDirectMessage(conversationId, request, principal);

    verify(conversationService).sendDirectMessage(senderId, conversationId, request);
  }

  @Test
  void sendDirectMessageRejectsNullPayload() {
    assertThatThrownBy(() -> controller.sendDirectMessage(conversationId, null, principal))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  void sendDirectMessageRequiresAuthenticationPrincipal() {
    DirectMessageSendRequest request = new DirectMessageSendRequest("hello");

    assertThatThrownBy(() -> controller.sendDirectMessage(
        conversationId,
        request,
        () -> "anonymous"
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
  }

  @Test
  void sendDirectMessageRejectsUserDetailsWithoutUser() {
    DirectMessageSendRequest request = new DirectMessageSendRequest("hello");
    Principal invalidPrincipal = new UsernamePasswordAuthenticationToken(
        new MoplUserDetails(null),
        null
    );

    assertThatThrownBy(() -> controller.sendDirectMessage(
        conversationId,
        request,
        invalidPrincipal
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
  }
}
