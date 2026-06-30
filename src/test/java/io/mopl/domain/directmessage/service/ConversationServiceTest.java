package io.mopl.domain.directmessage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.directmessage.dto.ConversationCreateRequest;
import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.mapper.ConversationMapper;
import io.mopl.domain.directmessage.repository.ConversationRepository;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
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
  private UserRepository userRepository;

  @Mock
  private ConversationMapper conversationMapper;

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
    when(conversationMapper.toDto(savedConversation, requester, withUser)).thenReturn(expected);

    ConversationDto result = conversationService.createConversation(
        requesterId,
        new ConversationCreateRequest(withUserId)
    );

    assertThat(result.id()).isNotNull();
    assertThat(result.with().userId()).isEqualTo(withUserId);
    assertThat(result.latestMessage()).isNull();
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
    when(conversationMapper.toDto(existingConversation, requester, withUser)).thenReturn(expected);

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
    when(conversationMapper.toDto(conversation, requester, withUser)).thenReturn(expected);
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
}
