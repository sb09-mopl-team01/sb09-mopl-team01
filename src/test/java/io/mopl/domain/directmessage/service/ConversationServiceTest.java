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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
  void createConversationRejectsSelfConversation() {
    UUID requesterId = UUID.randomUUID();
    User requester = createUser(requesterId, "requester");

    when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));

    assertThatThrownBy(() -> conversationService.createConversation(
        requesterId,
        new ConversationCreateRequest(requesterId)
    )).isInstanceOf(BaseException.class);
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
