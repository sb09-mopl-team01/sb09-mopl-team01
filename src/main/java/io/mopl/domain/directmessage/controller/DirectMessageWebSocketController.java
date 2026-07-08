package io.mopl.domain.directmessage.controller;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.dto.DirectMessageSendRequest;
import io.mopl.domain.directmessage.service.ConversationService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

  private static final String DIRECT_MESSAGE_TOPIC = "/sub/conversations/%s/direct-messages";

  private final ConversationService conversationService;
  private final SimpMessagingTemplate messagingTemplate;

  @MessageMapping("/conversations/{conversationId}/direct-messages")
  public void sendDirectMessage(
      @DestinationVariable UUID conversationId,
      DirectMessageSendRequest request,
      Principal principal
  ) {
    UUID senderId = resolveSenderId(principal);
    if (request == null) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    DirectMessageDto message = conversationService.sendDirectMessage(
        senderId,
        conversationId,
        request
    );
    messagingTemplate.convertAndSend(topic(conversationId), message);
  }

  private UUID resolveSenderId(Principal principal) {
    if (principal instanceof Authentication authentication
        && authentication.getPrincipal() instanceof MoplUserDetails userDetails) {
      if (userDetails.getUser() == null || userDetails.getUser().getId() == null) {
        throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
      }
      return userDetails.getUser().getId();
    }

    throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
  }

  private String topic(UUID conversationId) {
    return DIRECT_MESSAGE_TOPIC.formatted(conversationId);
  }
}
