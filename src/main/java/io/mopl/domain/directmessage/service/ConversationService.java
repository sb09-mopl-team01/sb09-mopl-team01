package io.mopl.domain.directmessage.service;

import io.mopl.domain.directmessage.dto.ConversationCreateRequest;
import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.dto.DirectMessageSendRequest;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.entity.DirectMessage;
import io.mopl.domain.directmessage.event.DirectMessageSentEvent;
import io.mopl.domain.directmessage.mapper.ConversationMapper;
import io.mopl.domain.directmessage.mapper.DirectMessageMapper;
import io.mopl.domain.directmessage.repository.ConversationRepository;
import io.mopl.domain.directmessage.repository.DirectMessageRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

  private static final String CREATED_AT_SORT = "createdAt";

  private final ConversationRepository conversationRepository;
  private final DirectMessageRepository directMessageRepository;
  private final UserRepository userRepository;
  private final ConversationMapper conversationMapper;
  private final DirectMessageMapper directMessageMapper;
  private final DomainEventPublisher eventPublisher;

  @Transactional
  public ConversationDto createConversation(UUID requesterId, ConversationCreateRequest request) {
    if (request == null || request.withUserId() == null) {
      log.warn("Invalid conversation create request. requesterId={}, request={}", requesterId, request);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    User requester = getUser(requesterId);
    User withUser = getUser(request.withUserId());
    validateParticipants(requester.getId(), withUser.getId());

    Conversation key = Conversation.between(requester.getId(), withUser.getId());
    Conversation conversation = conversationRepository
        .findByParticipantAIdAndParticipantBId(key.getParticipantAId(), key.getParticipantBId())
        .orElseGet(() -> saveConversation(key));
    validateConversationParticipant(conversation, requester.getId());
    log.debug("Conversation created or found. conversationId={}, requesterId={}, withUserId={}",
        conversation.getId(), requester.getId(), withUser.getId());

    return conversationMapper.toDto(
        conversation,
        withUser,
        findLastestMessageDto(conversation),
        false
    );
  }

  @Transactional(readOnly = true)
  public ConversationDto findConversationWithUser(UUID requesterId, UUID userId) {
    User requester = getUser(requesterId);
    User withUser = getUser(userId);
    validateParticipants(requester.getId(), withUser.getId());

    Conversation key = Conversation.between(requester.getId(), withUser.getId());
    Conversation conversation = conversationRepository
        .findByParticipantAIdAndParticipantBId(key.getParticipantAId(), key.getParticipantBId())
        .orElseThrow(() -> {
          log.warn("Conversation with user not found. requesterId={}, userId={}",
              requester.getId(), withUser.getId());
          return new BaseException(ErrorCode.CONVERSATION_NOT_FOUND);
        });

    return conversationMapper.toDto(
        conversation,
        withUser,
        findLastestMessageDto(conversation),
        false
    );
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
    log.debug("Conversation list requested. requesterId={}, keywordLike={}, cursor={}, idAfter={}, limit={}, sortDirection={}",
        requester.getId(), keywordLike, parsedCursor, idAfter, limit, sortDirection);
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
    Map<UUID, DirectMessageDto> lastestMessagesByConversationId = findLastestMessageDtos(pageData);
    List<ConversationDto> data = pageData.stream()
        .map(conversation -> conversationMapper.toDto(
            conversation,
            getOtherUser(usersById, conversation.getOtherParticipantId(requester.getId())),
            lastestMessagesByConversationId.get(conversation.getId()),
            false
        ))
        .toList();

    Conversation lastConversation = pageData.isEmpty() ? null : pageData.get(pageData.size() - 1);
    log.debug("Conversation list found. requesterId={}, size={}, hasNext={}",
        requester.getId(), pageData.size(), hasNext);

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
    validateConversationParticipant(conversation, requester.getId());
    log.debug("Conversation found. requesterId={}, conversationId={}",
        requester.getId(), conversation.getId());
    User withUser = getOtherUser(
        findOtherParticipants(requester.getId(), List.of(conversation)),
        conversation.getOtherParticipantId(requester.getId())
    );

    return conversationMapper.toDto(
        conversation,
        withUser,
        findLastestMessageDto(conversation),
        false
    );
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
    validateConversationParticipant(conversation, requester.getId());
    validateFindConversationsCommand(limit, sortDirection, sortBy);

    Instant parsedCursor = parseCursor(cursor);
    log.debug("Direct message list requested. requesterId={}, conversationId={}, cursor={}, idAfter={}, limit={}, sortDirection={}",
        requester.getId(), conversation.getId(), parsedCursor, idAfter, limit, sortDirection);
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
    log.debug("Direct message list found. requesterId={}, conversationId={}, size={}, hasNext={}",
        requester.getId(), conversation.getId(), pageData.size(), hasNext);

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
  public void sendDirectMessage(
      UUID senderId,
      UUID conversationId,
      DirectMessageSendRequest request
  ) {
    if (request == null) {
      log.warn("Invalid direct message send request. senderId={}, conversationId={}, request={}",
          senderId, conversationId, request);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    User sender = getUser(senderId);
    Conversation conversation = getConversation(conversationId);
    UUID receiverId = conversation.getOtherParticipantId(sender.getId());
    User receiver = getUser(receiverId);
    String content = validateMessageContent(request.content());

    DirectMessage directMessage = DirectMessage.create(
        conversation,
        sender.getId(),
        receiver.getId(),
        content
    );
    DirectMessage savedDirectMessage = directMessageRepository.save(directMessage);
    publishDirectMessageSent(savedDirectMessage, sender, receiver);
    log.debug("Direct message sent. directMessageId={}, conversationId={}, senderId={}, receiverId={}",
        savedDirectMessage.getId(), conversation.getId(), sender.getId(), receiver.getId());
  }

  @Transactional(readOnly = true)
  public DirectMessageDto findDirectMessage(UUID directMessageId) {
    if (directMessageId == null) {
      log.warn("Invalid direct message lookup. directMessageId={}", directMessageId);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    DirectMessage directMessage = directMessageRepository.findById(directMessageId)
        .orElseThrow(() -> {
          log.warn("Direct message not found. directMessageId={}", directMessageId);
          return new BaseException(ErrorCode.INVALID_INPUT);
        });
    User sender = getUser(directMessage.getSenderId());
    User receiver = getUser(directMessage.getReceiverId());

    return directMessageMapper.toDto(directMessage, sender, receiver);
  }

  @Transactional
  public void readDirectMessage(UUID requesterId, UUID conversationId, UUID directMessageId) {
    User requester = getUser(requesterId);
    Conversation conversation = getConversation(conversationId);
    validateConversationParticipant(conversation, requester.getId());

    DirectMessage directMessage = directMessageRepository.findById(directMessageId)
        .orElseThrow(() -> {
          log.warn("Direct message read target not found. requesterId={}, conversationId={}, directMessageId={}",
              requester.getId(), conversation.getId(), directMessageId);
          return new BaseException(ErrorCode.INVALID_INPUT);
        });

    validateDirectMessageReadTarget(conversation, directMessage, requester.getId());
    directMessage.markAsRead();
    log.debug("Direct message read. directMessageId={}, conversationId={}, requesterId={}",
        directMessage.getId(), conversation.getId(), requester.getId());
  }

  private Conversation saveConversation(Conversation conversation) {
    try {
      return conversationRepository.save(conversation);
    } catch (DataIntegrityViolationException e) {
      log.warn("Conversation create duplicated by concurrent request. participantAId={}, participantBId={}",
          conversation.getParticipantAId(), conversation.getParticipantBId());

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
      log.warn("Self conversation request denied. requesterId={}", requesterId);
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
      log.warn("Invalid direct message page request. limit={}, sortBy={}, sortDirection={}",
          limit, sortBy, sortDirection);
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
      log.warn("Invalid direct message read request. conversationId={}, directMessageId={}, requesterId={}, receiverId={}",
          conversation.getId(), directMessage.getId(), requesterId, directMessage.getReceiverId());
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private String validateMessageContent(String content) {
    if (!StringUtils.hasText(content)) {
      log.warn("Invalid direct message content. reason=blank");
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    String trimmedContent = content.trim();
    if (trimmedContent.length() > DirectMessage.CONTENT_MAX_LENGTH) {
      log.warn("Invalid direct message content. reason=too_long, length={}", trimmedContent.length());
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    return trimmedContent;
  }

  private void publishDirectMessageSent(
      DirectMessage directMessage,
      User sender,
      User receiver
  ) {
    eventPublisher.publish(new DirectMessageSentEvent(
        directMessage.getId(),
        directMessage.getConversation().getId(),
        sender.getId(),
        sender.getName(),
        receiver.getId(),
        Instant.now()
    ));
  }

  private Instant parseCursor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }

    try {
      return Instant.parse(cursor);
    } catch (DateTimeException e) {
      log.warn("Invalid direct message cursor format. cursor={}", cursor);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private User getUser(UUID userId) {
    if (userId == null) {
      log.warn("Invalid direct message user lookup. userId={}", userId);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    return userRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("Direct message user not found. userId={}", userId);
          return new UserNotFoundException();
        });
  }

  private Conversation getConversation(UUID conversationId) {
    if (conversationId == null) {
      log.warn("Invalid conversation lookup. conversationId={}", conversationId);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    return conversationRepository.findById(conversationId)
        .orElseThrow(() -> {
          log.warn("Conversation not found. conversationId={}", conversationId);
          return new BaseException(ErrorCode.CONVERSATION_NOT_FOUND);
        });
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
      log.warn("Direct message participant user not found. userId={}", userId);
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

  private Map<UUID, DirectMessageDto> findLastestMessageDtos(List<Conversation> conversations) {
    List<DirectMessage> lastestMessages = conversations.stream()
        .map(conversation -> directMessageRepository
            .findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId()))
        .flatMap(Optional::stream)
        .toList();

    if (lastestMessages.isEmpty()) {
      return Map.of();
    }

    Map<UUID, User> usersById = findMessageParticipants(lastestMessages);
    return lastestMessages.stream()
        .collect(Collectors.toMap(
            directMessage -> directMessage.getConversation().getId(),
            directMessage -> directMessageMapper.toDto(
                directMessage,
                getOtherUser(usersById, directMessage.getSenderId()),
                getOtherUser(usersById, directMessage.getReceiverId())
            )
        ));
  }

  private DirectMessageDto findLastestMessageDto(Conversation conversation) {
    return directMessageRepository
        .findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId())
        .map(directMessage -> directMessageMapper.toDto(
            directMessage,
            getUser(directMessage.getSenderId()),
            getUser(directMessage.getReceiverId())
        ))
        .orElse(null);
  }
}
