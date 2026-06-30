package io.mopl.domain.directmessage.service;

import io.mopl.domain.directmessage.dto.ConversationCreateRequest;
import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.mapper.ConversationMapper;
import io.mopl.domain.directmessage.repository.ConversationRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {

  private final ConversationRepository conversationRepository;
  private final UserRepository userRepository;
  private final ConversationMapper conversationMapper;

  @Transactional
  public ConversationDto createConversation(UUID requesterId, ConversationCreateRequest request) {
    User requester = getUser(requesterId);
    User withUser = getUser(request.withUserId());
    validateParticipants(requester.getId(), withUser.getId());

    Conversation key = Conversation.between(requester.getId(), withUser.getId());
    Conversation conversation = conversationRepository
        .findByParticipantAIdAndParticipantBId(key.getParticipantAId(), key.getParticipantBId())
        .orElseGet(() -> saveConversation(key));

    return conversationMapper.toDto(conversation, requester, withUser);
  }

  private Conversation saveConversation(Conversation conversation) {
    try {
      return conversationRepository.save(conversation);
    } catch (DataIntegrityViolationException e) {

      // 다른 트랜잭션이 이미 생성한 경우 재조회
      return conversationRepository
          .findByParticipantAIdAndParticipantBId(
              conversation.getParticipantAId(),
              conversation.getParticipantBId()
          )
          .orElseThrow(() -> new BaseException(ErrorCode.CONVERSATION_CREATE_RACE_CONDITION));
    }
  }

  private void validateParticipants(UUID requesterId, UUID withUserId) {
    if (requesterId.equals(withUserId)) {
      throw new BaseException(ErrorCode.SELF_CONVERSATION_NOT_ALLOWED);
    }
  }

  private User getUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(UserNotFoundException::new);
  }
}
