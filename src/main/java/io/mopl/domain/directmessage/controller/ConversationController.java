package io.mopl.domain.directmessage.controller;

import io.mopl.domain.directmessage.dto.ConversationCreateRequest;
import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.service.ConversationService;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class ConversationController {

  private final ConversationService conversationService;

  @PostMapping
  public ConversationDto createConversation(
      @RequestAttribute(name = "userId", required = true) UUID requesterId,
      @Valid @RequestBody ConversationCreateRequest request
  ) {
    return conversationService.createConversation(requesterId, request);
  }

  @GetMapping
  public CursorResponse<ConversationDto> findConversations(
      @RequestAttribute(name = "userId", required = true) UUID requesterId,
      @RequestParam(required = false) String keywordLike,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      @RequestParam SortDirection sortDirection,
      @RequestParam String sortBy
  ) {
    return conversationService.findConversations(
        requesterId,
        keywordLike,
        cursor,
        idAfter,
        limit,
        sortDirection,
        sortBy
    );
  }

  @GetMapping("/{conversationId}")
  public ConversationDto findConversation(
      @RequestAttribute(name = "userId", required = true) UUID requesterId,
      @PathVariable UUID conversationId
  ) {
    return conversationService.findConversation(requesterId, conversationId);
  }

  @GetMapping("/{conversationId}/direct-messages")
  public CursorResponse<DirectMessageDto> findDirectMessages(
      @RequestAttribute(name = "userId", required = true) UUID requesterId,
      @PathVariable UUID conversationId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      @RequestParam SortDirection sortDirection,
      @RequestParam String sortBy
  ) {
    return conversationService.findDirectMessages(
        requesterId,
        conversationId,
        cursor,
        idAfter,
        limit,
        sortDirection,
        sortBy
    );
  }

  @PostMapping("/{conversationId}/direct-messages/{directMessageId}/read")
  public void readDirectMessage(
      @RequestAttribute(name = "userId", required = true) UUID requesterId,
      @PathVariable UUID conversationId,
      @PathVariable UUID directMessageId
  ) {
    conversationService.readDirectMessage(requesterId, conversationId, directMessageId);
  }

}
