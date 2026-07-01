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
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ConversationService {

  private static final String CREATED_AT_SORT = "createdAt";

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

  @Transactional(readOnly = true)
  public CursorResponse<ConversationDto> findConversations(
      UUID requesterId,
      String keywordLike,
      String cursor,
      UUID idAfter,
      int limit,
      SortDirection sortDirection,
      String sortBy
  ) {
    User requester = getUser(requesterId);
    validateFindConversationsCommand(limit, sortDirection, sortBy);

    Instant parsedCursor = parseCursor(cursor);
    List<Conversation> conversations = conversationRepository.findMyConversationsWithCursor(
        requester.getId(),
        keywordLike,
        parsedCursor,
        idAfter,
        sortDirection,
        PageRequest.of(0, limit + 1)
    );

    boolean hasNext = conversations.size() > limit;
    List<Conversation> pageData = conversations.stream()
        .limit(limit)
        .toList();
    Map<UUID, User> usersById = findOtherParticipants(requester.getId(), pageData);
    List<ConversationDto> data = pageData.stream()
        .map(conversation -> conversationMapper.toDto(
            conversation,
            requester,
            getOtherUser(usersById, conversation.getOtherParticipantId(requester.getId()))
        ))
        .toList();

    Conversation lastConversation = pageData.isEmpty() ? null : pageData.get(pageData.size() - 1);

    return new CursorResponse<>(
        data,
        hasNext && lastConversation != null ? lastConversation.getCreatedAt().toString() : null,
        hasNext && lastConversation != null ? lastConversation.getId() : null,
        hasNext,
        conversationRepository.countMyConversations(requester.getId(), keywordLike),
        sortBy,
        sortDirection
    );
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

  private void validateFindConversationsCommand(
      int limit,
      SortDirection sortDirection,
      String sortBy
  ) {
    if (limit <= 0
        || sortDirection == null
        || !CREATED_AT_SORT.equals(sortBy)) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private Instant parseCursor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }

    try {
      return Instant.parse(cursor);
    } catch (DateTimeException e) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private User getUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(UserNotFoundException::new);
  }

  private Map<UUID, User> findOtherParticipants(UUID requesterId, List<Conversation> conversations) {
    List<UUID> userIds = conversations.stream()
        .map(conversation -> conversation.getOtherParticipantId(requesterId))
        .distinct()
        .toList();

    return userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
  }

  private User getOtherUser(Map<UUID, User> usersById, UUID userId) {
    User user = usersById.get(userId);
    if (user == null) {
      throw new UserNotFoundException();
    }
    return user;
  }
}
