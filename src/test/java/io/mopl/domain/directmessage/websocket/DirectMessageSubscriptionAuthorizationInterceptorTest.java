package io.mopl.domain.directmessage.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.mopl.domain.directmessage.repository.ConversationRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.global.security.MoplUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class DirectMessageSubscriptionAuthorizationInterceptorTest {

  private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
  private final DirectMessageSubscriptionAuthorizationInterceptor interceptor =
      new DirectMessageSubscriptionAuthorizationInterceptor(conversationRepository);

  private UUID userId;
  private UUID conversationId;
  private UsernamePasswordAuthenticationToken authentication;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    conversationId = UUID.randomUUID();
    User user = User.builder()
        .email("subscriber@example.com")
        .passwordHash("hash")
        .name("subscriber")
        .build();
    ReflectionTestUtils.setField(user, "id", userId);
    MoplUserDetails userDetails = new MoplUserDetails(user);
    authentication = new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );
  }

  @Test
  void preSendAllowsParticipantToSubscribeDirectMessageTopic() {
    Message<byte[]> message = subscribeMessage(
        "/sub/conversations/%s/direct-messages".formatted(conversationId),
        authentication
    );
    when(conversationRepository.existsByIdAndParticipantId(conversationId, userId))
        .thenReturn(true);

    Message<?> result = interceptor.preSend(message, null);

    assertThat(result).isEqualTo(message);
    verify(conversationRepository).existsByIdAndParticipantId(conversationId, userId);
  }

  @Test
  void preSendRejectsNonParticipantDirectMessageTopicSubscription() {
    Message<byte[]> message = subscribeMessage(
        "/sub/conversations/%s/direct-messages".formatted(conversationId),
        authentication
    );
    when(conversationRepository.existsByIdAndParticipantId(conversationId, userId))
        .thenReturn(false);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void preSendIgnoresOtherSubscriptions() {
    Message<byte[]> message = subscribeMessage("/sub/contents/%s/chat".formatted(conversationId), authentication);

    Message<?> result = interceptor.preSend(message, null);

    assertThat(result).isEqualTo(message);
    verifyNoInteractions(conversationRepository);
  }

  @Test
  void preSendRejectsUnauthenticatedDirectMessageTopicSubscription() {
    Message<byte[]> message = subscribeMessage(
        "/sub/conversations/%s/direct-messages".formatted(conversationId),
        null
    );

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void preSendRejectsInvalidConversationIdInDirectMessageTopic() {
    Message<byte[]> message = subscribeMessage(
        "/sub/conversations/not-a-uuid/direct-messages",
        authentication
    );

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  private Message<byte[]> subscribeMessage(
      String destination,
      UsernamePasswordAuthenticationToken authentication
  ) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination(destination);
    accessor.setUser(authentication);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
