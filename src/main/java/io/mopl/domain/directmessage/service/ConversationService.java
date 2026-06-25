package io.mopl.domain.directmessage.service;

import io.mopl.domain.directmessage.dto.ConversationCreateRequest;
import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.mapper.ConversationMapper;
import io.mopl.domain.directmessage.repository.ConversationRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {

  private final ConversationRepository conversationRepository;
  private final ConversationMapper conversationMapper;

  @Transactional
  public ConversationDto createConversation(UUID requesterId, ConversationCreateRequest request) {
    UUID withUserId = request.withUserId();
    validateParticipants(requesterId, withUserId);

    Conversation key = Conversation.between(requesterId, withUserId);
    Conversation conversation = conversationRepository
        .findByParticipantAIdAndParticipantBId(key.getParticipantAId(), key.getParticipantBId())
        .orElseGet(() -> conversationRepository.save(key));

    return conversationMapper.toDto(conversation, requesterId);
  }

  private void validateParticipants(UUID requesterId, UUID withUserId) {
    if (requesterId.equals(withUserId)) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }
}
