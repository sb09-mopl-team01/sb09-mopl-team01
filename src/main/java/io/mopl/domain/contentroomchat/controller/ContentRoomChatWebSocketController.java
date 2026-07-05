package io.mopl.domain.contentroomchat.controller;

import io.mopl.domain.contentroomchat.dto.ContentChatDto;
import io.mopl.domain.contentroomchat.dto.ContentChatSendRequest;
import io.mopl.domain.contentroomchat.service.ContentRoomChatService;
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
public class ContentRoomChatWebSocketController {

  private static final String CONTENT_ROOM_CHAT_TOPIC = "/sub/contents/%s/chat";

  private final ContentRoomChatService contentRoomChatService;
  private final SimpMessagingTemplate messagingTemplate;

  @MessageMapping("/contents/{contentId}/chat")
  public void sendMessage(
      @DestinationVariable UUID contentId,
      ContentChatSendRequest request,
      Principal principal
  ) {
    UUID senderId = resolveSenderId(principal);
    if (request == null) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    ContentChatDto message = contentRoomChatService.createChatMessage(
        senderId,
        contentId,
        request.content()
    );
    messagingTemplate.convertAndSend(topic(contentId), message);
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

  private String topic(UUID contentId) {
    return CONTENT_ROOM_CHAT_TOPIC.formatted(contentId);
  }
}
