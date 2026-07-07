package io.mopl.domain.directmessage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.dto.DirectMessageSendRequest;
import io.mopl.domain.directmessage.service.ConversationService;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class DirectMessageWebSocketControllerTest {

  private final ConversationService conversationService = mock(ConversationService.class);
  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final DirectMessageWebSocketController controller =
      new DirectMessageWebSocketController(conversationService, messagingTemplate);

  private UUID senderId;
  private UUID receiverId;
  private UUID conversationId;
  private Principal principal;

  @BeforeEach
  void setUp() {
    senderId = UUID.randomUUID();
    receiverId = UUID.randomUUID();
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
  void sendDirectMessageBroadcastsSavedMessageToConversationTopic() {
    DirectMessageSendRequest request = new DirectMessageSendRequest("hello");
    DirectMessageDto response = new DirectMessageDto(
        UUID.randomUUID(),
        conversationId,
        Instant.parse("2026-07-07T01:00:00Z"),
        UserSummary.builder()
            .userId(senderId)
            .name("sender")
            .profileImageUrl(null)
            .build(),
        UserSummary.builder()
            .userId(receiverId)
            .name("receiver")
            .profileImageUrl(null)
            .build(),
        "hello"
    );

    when(conversationService.sendDirectMessage(senderId, conversationId, request))
        .thenReturn(response);

    controller.sendDirectMessage(conversationId, request, principal);

    verify(conversationService).sendDirectMessage(senderId, conversationId, request);
    verify(messagingTemplate).convertAndSend(
        "/sub/conversations/%s/direct-messages".formatted(conversationId),
        response
    );
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
