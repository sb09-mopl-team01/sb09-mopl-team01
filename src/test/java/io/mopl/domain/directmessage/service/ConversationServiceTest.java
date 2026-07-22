package io.mopl.domain.directmessage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

  @InjectMocks
  private ConversationService conversationService;

  @Mock
  private ConversationRepository conversationRepository;

  @Mock
  private DirectMessageRepository directMessageRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ConversationMapper conversationMapper;

  @Mock
  private DirectMessageMapper directMessageMapper;

  @Mock
  private DomainEventPublisher eventPublisher;

  private UUID requesterId;
  private UUID withUserId;
  private User requester;
  private User withUser;

  @BeforeEach
  void setUp() {
    requesterId = UUID.randomUUID();
    withUserId = UUID.randomUUID();
    requester = createUser(requesterId, "requester");
    withUser = createUser(withUserId, "receiver");
  }

  @Test
  void createConversationCreatesNewConversation() {
    Conversation savedConversation = Conversation.between(requesterId, withUserId);
    ReflectionTestUtils.setField(savedConversation, "id", UUID.randomUUID());
    ConversationDto expected = createConversationDto(savedConversation.getId());

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(userRepository.findById(withUserId)).thenReturn(Optional.of(withUser));
    when(conversationRepository.findByParticipantAIdAndParticipantBId(any(), any()))
        .thenReturn(Optional.empty());
    when(conversationRepository.save(any(Conversation.class))).thenReturn(savedConversation);
    when(directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(
        savedConversation.getId()
    )).thenReturn(Optional.empty());
    when(conversationMapper.toDto(savedConversation, withUser, null, false)).thenReturn(expected);

    ConversationDto result = conversationService.createConversation(
        requesterId,
        new ConversationCreateRequest(withUserId)
    );

    assertThat(result.id()).isNotNull();
    assertThat(result.with().userId()).isEqualTo(withUserId);
    assertThat(result.lastestMessage()).isNull();
    assertThat(result.hasUnread()).isFalse();
    verify(conversationRepository).save(any(Conversation.class));
  }

  @Test
  void createConversationReturnsExistingConversationForSameParticipants() {
    Conversation existingConversation = Conversation.between(requesterId, withUserId);
    ReflectionTestUtils.setField(existingConversation, "id", UUID.randomUUID());
    ConversationDto expected = createConversationDto(existingConversation.getId());

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(userRepository.findById(withUserId)).thenReturn(Optional.of(withUser));
    when(conversationRepository.findByParticipantAIdAndParticipantBId(any(), any()))
        .thenReturn(Optional.of(existingConversation));
    when(directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(
        existingConversation.getId()
    )).thenReturn(Optional.empty());
    when(conversationMapper.toDto(existingConversation, withUser, null, false)).thenReturn(expected);

    ConversationDto result = conversationService.createConversation(
        requesterId,
        new ConversationCreateRequest(withUserId));

    assertThat(result.id()).isEqualTo(existingConversation.getId());
  }

  @Test
  void createConversationRejectsSelfConversationWithDomainErrorCode() {
    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));

    assertThatThrownBy(() -> conversationService.createConversation(
        requesterId,
        new ConversationCreateRequest(requesterId)
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.SELF_CONVERSATION_NOT_ALLOWED);
  }

  @Test
  void createConversationRejectsNullRequest() {
    assertThatThrownBy(() -> conversationService.createConversation(requesterId, null))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  void createConversationThrowsDomainErrorWhenRaceConditionRequeryFails() {
    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(userRepository.findById(withUserId)).thenReturn(Optional.of(withUser));
    when(conversationRepository.findByParticipantAIdAndParticipantBId(any(), any()))
        .thenReturn(Optional.empty());
    when(conversationRepository.save(any(Conversation.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate conversation"));

    assertThatThrownBy(() -> conversationService.createConversation(
        requesterId,
        new ConversationCreateRequest(withUserId)
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONVERSATION_CREATE_RACE_CONDITION);
  }

  @Test
  void findConversationsReturnsOnlyRequesterConversationsWithCursorResponse() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    Instant createdAt = Instant.now();
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    ReflectionTestUtils.setField(conversation, "createdAt", createdAt);
    ConversationDto expected = createConversationDto(conversationId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findMyConversationsWithCursor(
        requesterId,
        "receiver",
        null,
        null,
        SortDirection.DESCENDING,
        PageRequest.of(0, 2)
    )).thenReturn(List.of(conversation));
    when(userRepository.findAllById(List.of(withUserId))).thenReturn(List.of(withUser));
    when(directMessageRepository.findLastestByConversationIds(List.of(conversationId)))
        .thenReturn(List.of());
    when(conversationMapper.toDto(conversation, withUser, null, false)).thenReturn(expected);
    when(conversationRepository.countMyConversations(requesterId, "receiver")).thenReturn(1L);

    CursorResponse<ConversationDto> result = conversationService.findConversations(
        requesterId,
        "receiver",
        null,
        null,
        1,
        SortDirection.DESCENDING,
        "createdAt"
    );

    assertThat(result.data()).containsExactly(expected);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.nextIdAfter()).isNull();
    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);
  }

  @Test
  void findConversationsIncludesLastestMessage() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    Instant conversationCreatedAt = Instant.now().minusSeconds(60);
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    ReflectionTestUtils.setField(conversation, "createdAt", conversationCreatedAt);
    DirectMessage lastestMessage = DirectMessage.create(conversation, withUserId, requesterId, "last message");
    UUID directMessageId = UUID.randomUUID();
    Instant messageCreatedAt = Instant.now();
    ReflectionTestUtils.setField(lastestMessage, "id", directMessageId);
    ReflectionTestUtils.setField(lastestMessage, "createdAt", messageCreatedAt);
    DirectMessageDto lastestMessageDto = createDirectMessageDto(
        directMessageId,
        conversationId,
        messageCreatedAt
    );
    ConversationDto expected = new ConversationDto(
        conversationId,
        UserSummary.builder()
            .userId(withUserId)
            .name(withUser.getName())
            .profileImageUrl(withUser.getProfileImageUrl())
            .build(),
        lastestMessageDto,
        false
    );

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findMyConversationsWithCursor(
        requesterId,
        null,
        null,
        null,
        SortDirection.DESCENDING,
        PageRequest.of(0, 2)
    )).thenReturn(List.of(conversation));
    when(userRepository.findAllById(List.of(withUserId))).thenReturn(List.of(withUser));
    when(directMessageRepository.findLastestByConversationIds(List.of(conversationId)))
        .thenReturn(List.of(lastestMessage));
    when(userRepository.findAllById(List.of(withUserId, requesterId)))
        .thenReturn(List.of(withUser, requester));
    when(directMessageMapper.toDto(lastestMessage, withUser, requester)).thenReturn(lastestMessageDto);
    when(conversationMapper.toDto(conversation, withUser, lastestMessageDto, false))
        .thenReturn(expected);
    when(conversationRepository.countMyConversations(requesterId, null)).thenReturn(1L);

    CursorResponse<ConversationDto> result = conversationService.findConversations(
        requesterId,
        null,
        null,
        null,
        1,
        SortDirection.DESCENDING,
        "createdAt"
    );

    assertThat(result.data()).containsExactly(expected);
    assertThat(result.data().get(0).lastestMessage()).isEqualTo(lastestMessageDto);
  }

  @Test
  void findConversationsKeepsLastestMessageNullWhenConversationHasNoMessage() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    ReflectionTestUtils.setField(conversation, "createdAt", Instant.now());
    ConversationDto expected = createConversationDto(conversationId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findMyConversationsWithCursor(
        requesterId,
        null,
        null,
        null,
        SortDirection.DESCENDING,
        PageRequest.of(0, 2)
    )).thenReturn(List.of(conversation));
    when(userRepository.findAllById(List.of(withUserId))).thenReturn(List.of(withUser));
    when(directMessageRepository.findLastestByConversationIds(List.of(conversationId)))
        .thenReturn(List.of());
    when(conversationMapper.toDto(conversation, withUser, null, false)).thenReturn(expected);
    when(conversationRepository.countMyConversations(requesterId, null)).thenReturn(1L);

    CursorResponse<ConversationDto> result = conversationService.findConversations(
        requesterId,
        null,
        null,
        null,
        1,
        SortDirection.DESCENDING,
        "createdAt"
    );

    assertThat(result.data()).containsExactly(expected);
    assertThat(result.data().get(0).lastestMessage()).isNull();
  }

  @Test
  void findConversationsSelectsLastestMessageByIdDescWhenCreatedAtIsSame() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    ReflectionTestUtils.setField(conversation, "createdAt", Instant.now());
    Instant sameCreatedAt = Instant.parse("2026-07-22T01:00:00Z");

    DirectMessage lowerIdMessage = DirectMessage.create(conversation, withUserId, requesterId, "lower");
    DirectMessage higherIdMessage = DirectMessage.create(conversation, withUserId, requesterId, "higher");
    UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    ReflectionTestUtils.setField(lowerIdMessage, "id", lowerId);
    ReflectionTestUtils.setField(higherIdMessage, "id", higherId);
    ReflectionTestUtils.setField(lowerIdMessage, "createdAt", sameCreatedAt);
    ReflectionTestUtils.setField(higherIdMessage, "createdAt", sameCreatedAt);

    DirectMessageDto selectedDto = createDirectMessageDto(higherId, conversationId, sameCreatedAt);
    ConversationDto expected = new ConversationDto(
        conversationId,
        UserSummary.builder()
            .userId(withUserId)
            .name(withUser.getName())
            .profileImageUrl(withUser.getProfileImageUrl())
            .build(),
        selectedDto,
        false
    );

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findMyConversationsWithCursor(
        requesterId,
        null,
        null,
        null,
        SortDirection.DESCENDING,
        PageRequest.of(0, 2)
    )).thenReturn(List.of(conversation));
    when(userRepository.findAllById(List.of(withUserId))).thenReturn(List.of(withUser));
    when(directMessageRepository.findLastestByConversationIds(List.of(conversationId)))
        .thenReturn(List.of(lowerIdMessage, higherIdMessage));
    when(userRepository.findAllById(List.of(withUserId, requesterId)))
        .thenReturn(List.of(withUser, requester));
    when(directMessageMapper.toDto(higherIdMessage, withUser, requester)).thenReturn(selectedDto);
    when(conversationMapper.toDto(conversation, withUser, selectedDto, false)).thenReturn(expected);
    when(conversationRepository.countMyConversations(requesterId, null)).thenReturn(1L);

    CursorResponse<ConversationDto> result = conversationService.findConversations(
        requesterId,
        null,
        null,
        null,
        1,
        SortDirection.DESCENDING,
        "createdAt"
    );

    assertThat(result.data()).containsExactly(expected);
    assertThat(result.data().get(0).lastestMessage()).isEqualTo(selectedDto);
    verify(directMessageMapper, never()).toDto(lowerIdMessage, withUser, requester);
  }

  @Test
  void findConversationsMapsOneLastestMessagePerConversation() {
    UUID thirdUserId = UUID.randomUUID();
    User thirdUser = createUser(thirdUserId, "third");
    Conversation firstConversation = Conversation.between(requesterId, withUserId);
    Conversation secondConversation = Conversation.between(requesterId, thirdUserId);
    UUID firstConversationId = UUID.randomUUID();
    UUID secondConversationId = UUID.randomUUID();
    Instant now = Instant.now();
    ReflectionTestUtils.setField(firstConversation, "id", firstConversationId);
    ReflectionTestUtils.setField(secondConversation, "id", secondConversationId);
    ReflectionTestUtils.setField(firstConversation, "createdAt", now.minusSeconds(10));
    ReflectionTestUtils.setField(secondConversation, "createdAt", now.minusSeconds(20));

    DirectMessage firstLastestMessage = DirectMessage.create(
        firstConversation,
        withUserId,
        requesterId,
        "first"
    );
    DirectMessage secondLastestMessage = DirectMessage.create(
        secondConversation,
        thirdUserId,
        requesterId,
        "second"
    );
    UUID firstMessageId = UUID.randomUUID();
    UUID secondMessageId = UUID.randomUUID();
    ReflectionTestUtils.setField(firstLastestMessage, "id", firstMessageId);
    ReflectionTestUtils.setField(secondLastestMessage, "id", secondMessageId);
    ReflectionTestUtils.setField(firstLastestMessage, "createdAt", now);
    ReflectionTestUtils.setField(secondLastestMessage, "createdAt", now.minusSeconds(5));

    DirectMessageDto firstMessageDto = createDirectMessageDto(firstMessageId, firstConversationId, now);
    DirectMessageDto secondMessageDto = new DirectMessageDto(
        secondMessageId,
        secondConversationId,
        now.minusSeconds(5),
        UserSummary.builder()
            .userId(thirdUserId)
            .name(thirdUser.getName())
            .profileImageUrl(thirdUser.getProfileImageUrl())
            .build(),
        UserSummary.builder()
            .userId(requesterId)
            .name(requester.getName())
            .profileImageUrl(requester.getProfileImageUrl())
            .build(),
        "second"
    );
    ConversationDto firstExpected = new ConversationDto(
        firstConversationId,
        UserSummary.builder()
            .userId(withUserId)
            .name(withUser.getName())
            .profileImageUrl(withUser.getProfileImageUrl())
            .build(),
        firstMessageDto,
        false
    );
    ConversationDto secondExpected = new ConversationDto(
        secondConversationId,
        UserSummary.builder()
            .userId(thirdUserId)
            .name(thirdUser.getName())
            .profileImageUrl(thirdUser.getProfileImageUrl())
            .build(),
        secondMessageDto,
        false
    );

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findMyConversationsWithCursor(
        requesterId,
        null,
        null,
        null,
        SortDirection.DESCENDING,
        PageRequest.of(0, 3)
    )).thenReturn(List.of(firstConversation, secondConversation));
    when(userRepository.findAllById(List.of(withUserId, thirdUserId)))
        .thenReturn(List.of(withUser, thirdUser));
    when(directMessageRepository.findLastestByConversationIds(List.of(firstConversationId, secondConversationId)))
        .thenReturn(List.of(firstLastestMessage, secondLastestMessage));
    when(userRepository.findAllById(List.of(withUserId, requesterId, thirdUserId)))
        .thenReturn(List.of(withUser, requester, thirdUser));
    when(directMessageMapper.toDto(firstLastestMessage, withUser, requester)).thenReturn(firstMessageDto);
    when(directMessageMapper.toDto(secondLastestMessage, thirdUser, requester)).thenReturn(secondMessageDto);
    when(conversationMapper.toDto(firstConversation, withUser, firstMessageDto, false))
        .thenReturn(firstExpected);
    when(conversationMapper.toDto(secondConversation, thirdUser, secondMessageDto, false))
        .thenReturn(secondExpected);
    when(conversationRepository.countMyConversations(requesterId, null)).thenReturn(2L);

    CursorResponse<ConversationDto> result = conversationService.findConversations(
        requesterId,
        null,
        null,
        null,
        2,
        SortDirection.DESCENDING,
        "createdAt"
    );

    assertThat(result.data()).containsExactly(firstExpected, secondExpected);
    assertThat(result.data())
        .extracting(ConversationDto::lastestMessage)
        .containsExactly(firstMessageDto, secondMessageDto);
  }

  @Test
  void findConversationReturnsConversationWhenRequesterIsParticipant() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    ConversationDto expected = createConversationDto(conversationId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
    when(userRepository.findAllById(List.of(withUserId))).thenReturn(List.of(withUser));
    when(directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversationId))
        .thenReturn(Optional.empty());
    when(conversationMapper.toDto(conversation, withUser, null, false)).thenReturn(expected);

    ConversationDto result = conversationService.findConversation(requesterId, conversationId);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void findConversationWithUserReturnsConversationWhenExists() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    ConversationDto expected = createConversationDto(conversationId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(userRepository.findById(withUserId)).thenReturn(Optional.of(withUser));
    when(conversationRepository.findByParticipantAIdAndParticipantBId(any(), any()))
        .thenReturn(Optional.of(conversation));
    when(directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversationId))
        .thenReturn(Optional.empty());
    when(conversationMapper.toDto(conversation, withUser, null, false)).thenReturn(expected);

    ConversationDto result = conversationService.findConversationWithUser(requesterId, withUserId);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void findConversationWithUserThrowsNotFoundWhenConversationDoesNotExist() {
    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(userRepository.findById(withUserId)).thenReturn(Optional.of(withUser));
    when(conversationRepository.findByParticipantAIdAndParticipantBId(any(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.findConversationWithUser(requesterId, withUserId))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
  }

  @Test
  void findDirectMessagesReturnsCursorResponseWhenRequesterIsParticipant() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    DirectMessage directMessage = DirectMessage.create(conversation, requesterId, withUserId, "hello");
    UUID directMessageId = UUID.randomUUID();
    Instant createdAt = Instant.now();
    ReflectionTestUtils.setField(directMessage, "id", directMessageId);
    ReflectionTestUtils.setField(directMessage, "createdAt", createdAt);
    DirectMessageDto expected = createDirectMessageDto(directMessageId, conversationId, createdAt);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
    when(directMessageRepository.findByConversationIdWithCursor(
        conversationId,
        null,
        null,
        SortDirection.ASCENDING,
        PageRequest.of(0, 2)
    )).thenReturn(List.of(directMessage));
    when(userRepository.findAllById(List.of(requesterId, withUserId)))
        .thenReturn(List.of(requester, withUser));
    when(directMessageMapper.toDto(directMessage, requester, withUser)).thenReturn(expected);
    when(directMessageRepository.countByConversationId(conversationId)).thenReturn(1L);

    CursorResponse<DirectMessageDto> result = conversationService.findDirectMessages(
        requesterId,
        conversationId,
        null,
        null,
        1,
        SortDirection.ASCENDING,
        "createdAt"
    );

    assertThat(result.data()).containsExactly(expected);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo(SortDirection.ASCENDING);
  }

  @Test
  void findDirectMessagesRejectsWhenRequesterIsNotParticipant() {
    UUID otherUserId = UUID.randomUUID();
    Conversation conversation = Conversation.between(withUserId, otherUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> conversationService.findDirectMessages(
        requesterId,
        conversationId,
        null,
        null,
        1,
        SortDirection.ASCENDING,
        "createdAt"
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_CHAT_PARTICIPANT);
  }

  @Test
  void findDirectMessagesThrowsConversationNotFoundWhenConversationDoesNotExist() {
    UUID conversationId = UUID.randomUUID();

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.findDirectMessages(
        requesterId,
        conversationId,
        null,
        null,
        1,
        SortDirection.ASCENDING,
        "createdAt"
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
  }

  @Test
  void sendDirectMessageSavesMessageWhenSenderIsParticipant() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    DirectMessage savedDirectMessage = DirectMessage.create(
        conversation,
        requesterId,
        withUserId,
        "hello"
    );
    UUID directMessageId = UUID.randomUUID();
    Instant createdAt = Instant.now();
    ReflectionTestUtils.setField(savedDirectMessage, "id", directMessageId);
    ReflectionTestUtils.setField(savedDirectMessage, "createdAt", createdAt);
    DirectMessageDto expected = createDirectMessageDto(directMessageId, conversationId, createdAt);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
    when(userRepository.findById(withUserId)).thenReturn(Optional.of(withUser));
    when(directMessageRepository.save(any(DirectMessage.class))).thenReturn(savedDirectMessage);
    when(directMessageMapper.toDto(savedDirectMessage, requester, withUser)).thenReturn(expected);

    DirectMessageDto result = conversationService.sendDirectMessage(
        requesterId,
        conversationId,
        new DirectMessageSendRequest("  hello  ")
    );

    ArgumentCaptor<DirectMessage> directMessageCaptor = ArgumentCaptor.forClass(DirectMessage.class);
    verify(directMessageRepository).save(directMessageCaptor.capture());
    DirectMessage capturedDirectMessage = directMessageCaptor.getValue();
    assertThat(capturedDirectMessage.getConversation()).isEqualTo(conversation);
    assertThat(capturedDirectMessage.getSenderId()).isEqualTo(requesterId);
    assertThat(capturedDirectMessage.getReceiverId()).isEqualTo(withUserId);
    assertThat(capturedDirectMessage.getContent()).isEqualTo("hello");
    assertThat(result).isEqualTo(expected);
    verify(eventPublisher).publish(argThat(event -> event instanceof DirectMessageSentEvent sentEvent
        && sentEvent.directMessageId().equals(directMessageId)
        && sentEvent.conversationId().equals(conversationId)
        && sentEvent.senderId().equals(requesterId)
        && sentEvent.senderName().equals(requester.getName())
        && sentEvent.receiverId().equals(withUserId)
        && sentEvent.occurredAt() != null));
  }

  @Test
  void sendDirectMessageRejectsBlankContent() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
    when(userRepository.findById(withUserId)).thenReturn(Optional.of(withUser));

    assertThatThrownBy(() -> conversationService.sendDirectMessage(
        requesterId,
        conversationId,
        new DirectMessageSendRequest("   ")
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  void sendDirectMessageRejectsNullRequest() {
    UUID conversationId = UUID.randomUUID();

    assertThatThrownBy(() -> conversationService.sendDirectMessage(
        requesterId,
        conversationId,
        null
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  void sendDirectMessageThrowsConversationNotFoundWhenConversationDoesNotExist() {
    UUID conversationId = UUID.randomUUID();

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.sendDirectMessage(
        requesterId,
        conversationId,
        new DirectMessageSendRequest("hello")
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
  }

  @Test
  void sendDirectMessageRejectsContentLongerThanMaxLength() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
    when(userRepository.findById(withUserId)).thenReturn(Optional.of(withUser));

    assertThatThrownBy(() -> conversationService.sendDirectMessage(
        requesterId,
        conversationId,
        new DirectMessageSendRequest("a".repeat(1001))
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  void sendDirectMessageRejectsWhenSenderIsNotParticipant() {
    UUID otherUserId = UUID.randomUUID();
    Conversation conversation = Conversation.between(withUserId, otherUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> conversationService.sendDirectMessage(
        requesterId,
        conversationId,
        new DirectMessageSendRequest("hello")
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_CHAT_PARTICIPANT);
  }

  @Test
  void readDirectMessageMarksMessageAsReadWhenRequesterIsReceiver() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    DirectMessage directMessage = DirectMessage.create(conversation, withUserId, requesterId, "hello");
    UUID directMessageId = UUID.randomUUID();
    ReflectionTestUtils.setField(directMessage, "id", directMessageId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
    when(directMessageRepository.findById(directMessageId)).thenReturn(Optional.of(directMessage));

    conversationService.readDirectMessage(requesterId, conversationId, directMessageId);

    assertThat(directMessage.isRead()).isTrue();
  }

  @Test
  void readDirectMessageRejectsWhenRequesterIsSender() {
    Conversation conversation = Conversation.between(requesterId, withUserId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);
    DirectMessage directMessage = DirectMessage.create(conversation, requesterId, withUserId, "hello");
    UUID directMessageId = UUID.randomUUID();
    ReflectionTestUtils.setField(directMessage, "id", directMessageId);

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
    when(directMessageRepository.findById(directMessageId)).thenReturn(Optional.of(directMessage));

    assertThatThrownBy(() -> conversationService.readDirectMessage(
        requesterId,
        conversationId,
        directMessageId
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  private User createUser(UUID id, String name) {
    User user = User.builder()
        .email(name + "@example.com")
        .passwordHash("password")
        .name(name)
        .build();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private ConversationDto createConversationDto(UUID conversationId) {
    return new ConversationDto(
        conversationId,
        UserSummary.builder()
            .userId(withUserId)
            .name(withUser.getName())
            .profileImageUrl(withUser.getProfileImageUrl())
            .build(),
        null,
        false
    );
  }

  private DirectMessageDto createDirectMessageDto(
      UUID directMessageId,
      UUID conversationId,
      Instant createdAt
  ) {
    return new DirectMessageDto(
        directMessageId,
        conversationId,
        createdAt,
        UserSummary.builder()
            .userId(requesterId)
            .name(requester.getName())
            .profileImageUrl(requester.getProfileImageUrl())
            .build(),
        UserSummary.builder()
            .userId(withUserId)
            .name(withUser.getName())
            .profileImageUrl(withUser.getProfileImageUrl())
            .build(),
        "hello"
    );
  }
}
