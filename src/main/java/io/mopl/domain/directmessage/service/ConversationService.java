package io.mopl.domain.directmessage.service;

import io.mopl.domain.directmessage.dto.ConversationCreateRequest;
import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.entity.DirectMessage;
import io.mopl.domain.directmessage.mapper.ConversationMapper;
import io.mopl.domain.directmessage.mapper.DirectMessageMapper;
import io.mopl.domain.directmessage.repository.ConversationRepository;
import io.mopl.domain.directmessage.repository.DirectMessageRepository;
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
  private final DirectMessageRepository directMessageRepository;
  private final UserRepository userRepository;
  private final ConversationMapper conversationMapper;
  private final DirectMessageMapper directMessageMapper;

  @Transactional
  public ConversationDto createConversation(UUID requesterId, ConversationCreateRequest request) {
    User requester = getUser(requesterId);
    User withUser = getUser(request.withUserId());
    validateParticipants(requester.getId(), withUser.getId());

    Conversation key = Conversation.between(requester.getId(), withUser.getId());
    Conversation conversation = conversationRepository
        .findByParticipantAIdAndParticipantBId(key.getParticipantAId(), key.getParticipantBId())
        .orElseGet(() -> saveConversation(key));
    validateConversationParticipant(conversation, requester.getId());

    return conversationMapper.toDto(conversation, withUser);
  }

  //대화방 목록 조회
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

  @Transactional(readOnly = true)
  public ConversationDto findConversation(UUID requesterId, UUID conversationId) {
    User requester = getUser(requesterId);
    Conversation conversation = getConversation(conversationId);
    User withUser = getOtherUser(
        findOtherParticipants(requester.getId(), List.of(conversation)),
        conversation.getOtherParticipantId(requester.getId())
    );

    return conversationMapper.toDto(conversation, requester, withUser);
  }

  @Transactional(readOnly = true)
  public CursorResponse<DirectMessageDto> findDirectMessages(
      UUID requesterId,
      UUID conversationId,
      String cursor,
      UUID idAfter,
      int limit,
      SortDirection sortDirection,
      String sortBy
  ) {
    User requester = getUser(requesterId);
    Conversation conversation = getConversation(conversationId);
    conversation.getOtherParticipantId(requester.getId());
    validateFindConversationsCommand(limit, sortDirection, sortBy);

    Instant parsedCursor = parseCursor(cursor);
    List<DirectMessage> directMessages = directMessageRepository.findByConversationIdWithCursor(
        conversation.getId(),
        parsedCursor,
        idAfter,
        sortDirection,
        PageRequest.of(0, limit + 1)
    );

    boolean hasNext = directMessages.size() > limit;
    List<DirectMessage> pageData = directMessages.stream()
        .limit(limit)
        .toList();
    Map<UUID, User> usersById = findMessageParticipants(pageData);
    List<DirectMessageDto> data = pageData.stream()
        .map(directMessage -> directMessageMapper.toDto(
            directMessage,
            getOtherUser(usersById, directMessage.getSenderId()),
            getOtherUser(usersById, directMessage.getReceiverId())
        ))
        .toList();

    DirectMessage lastDirectMessage = pageData.isEmpty() ? null : pageData.get(pageData.size() - 1);

    return new CursorResponse<>(
        data,
        hasNext && lastDirectMessage != null ? lastDirectMessage.getCreatedAt().toString() : null,
        hasNext && lastDirectMessage != null ? lastDirectMessage.getId() : null,
        hasNext,
        directMessageRepository.countByConversationId(conversation.getId()),
        sortBy,
        sortDirection
    );
  }

  @Transactional
  public void readDirectMessage(UUID requesterId, UUID conversationId, UUID directMessageId) {
    User requester = getUser(requesterId);
    Conversation conversation = getConversation(conversationId);
    conversation.getOtherParticipantId(requester.getId());

    DirectMessage directMessage = directMessageRepository.findById(directMessageId)
        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));

    validateDirectMessageReadTarget(conversation, directMessage, requester.getId());
    directMessage.markAsRead();

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

  private void validateConversationParticipant(Conversation conversation, UUID requesterId) {
    conversation.getOtherParticipantId(requesterId);
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

  private void validateDirectMessageReadTarget(
      Conversation conversation,
      DirectMessage directMessage,
      UUID requesterId
  ) {
    if (!directMessage.getConversation().getId().equals(conversation.getId())
        || !directMessage.getReceiverId().equals(requesterId)) {
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

  private Conversation getConversation(UUID conversationId) {
    return conversationRepository.findById(conversationId)
        .orElseThrow(() -> new BaseException(ErrorCode.CONVERSATION_NOT_FOUND));
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

  private Map<UUID, User> findMessageParticipants(List<DirectMessage> directMessages) {
    List<UUID> userIds = directMessages.stream()
        .flatMap(directMessage -> List.of(
            directMessage.getSenderId(),
            directMessage.getReceiverId()
        ).stream())
        .distinct()
        .toList();

    return userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
  }
}
