package io.mopl.domain.directmessage.controller;

import io.mopl.domain.directmessage.dto.ConversationCreateRequest;
import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.service.ConversationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class ConversationController {

  private final ConversationService conversationService;

  @PostMapping
  public ConversationDto createConversation(
      @RequestAttribute("userId") UUID requesterId,
      @Valid @RequestBody ConversationCreateRequest request
  ) {
    return conversationService.createConversation(requesterId, request);
  }
}
