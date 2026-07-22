package io.mopl.domain.contentroomchat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.mopl.domain.user.entity.User;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import io.mopl.global.security.MoplUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class ContentRoomChatSubscriptionAuthorizationInterceptorTest {

  private final WatchingSessionRepository watchingSessionRepository =
      mock(WatchingSessionRepository.class);
  private final ContentRoomChatSubscriptionAuthorizationInterceptor interceptor =
      new ContentRoomChatSubscriptionAuthorizationInterceptor(watchingSessionRepository);
  private final ExecutorSubscribableChannel inboundChannel = new ExecutorSubscribableChannel();

  private UUID subscriberId;
  private UUID contentId;
  private UsernamePasswordAuthenticationToken authentication;

  @BeforeEach
  void setUp() {
    inboundChannel.addInterceptor(interceptor);
    subscriberId = UUID.randomUUID();
    contentId = UUID.randomUUID();
    User subscriber = User.builder()
        .email("subscriber@example.com")
        .passwordHash("hash")
        .name("subscriber")
        .build();
    ReflectionTestUtils.setField(subscriber, "id", subscriberId);
    MoplUserDetails userDetails = new MoplUserDetails(subscriber);
    authentication = new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );
  }

  @Test
  void inboundSubscribeAllowsCurrentWatchingSessionParticipant() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/chat".formatted(contentId),
        authentication
    );
    when(watchingSessionRepository.existsByWatcherIdAndContentId(subscriberId, contentId))
        .thenReturn(true);

    boolean sent = inboundChannel.send(message);

    assertThat(sent).isTrue();
    verify(watchingSessionRepository).existsByWatcherIdAndContentId(subscriberId, contentId);
  }

  @Test
  void inboundSubscribeRejectsNonParticipant() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/chat".formatted(contentId),
        authentication
    );
    when(watchingSessionRepository.existsByWatcherIdAndContentId(subscriberId, contentId))
        .thenReturn(false);

    assertThatThrownBy(() -> inboundChannel.send(message))
        .isInstanceOf(MessageDeliveryException.class)
        .hasCauseInstanceOf(AccessDeniedException.class);
  }

  @Test
  void inboundSubscribeRejectsUnauthenticatedUser() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/chat".formatted(contentId),
        null
    );

    assertThatThrownBy(() -> inboundChannel.send(message))
        .isInstanceOf(MessageDeliveryException.class)
        .hasCauseInstanceOf(AuthenticationCredentialsNotFoundException.class);
    verifyNoInteractions(watchingSessionRepository);
  }

  @Test
  void inboundSubscribeRejectsUnauthenticatedAuthenticationToken() {
    UsernamePasswordAuthenticationToken unauthenticated =
        new UsernamePasswordAuthenticationToken(authentication.getPrincipal(), null);
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/chat".formatted(contentId),
        unauthenticated
    );

    assertThatThrownBy(() -> inboundChannel.send(message))
        .isInstanceOf(MessageDeliveryException.class)
        .hasCauseInstanceOf(AuthenticationCredentialsNotFoundException.class);
    verifyNoInteractions(watchingSessionRepository);
  }

  @Test
  void inboundSubscribeRejectsInvalidContentId() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/not-a-uuid/chat",
        authentication
    );

    assertThatThrownBy(() -> inboundChannel.send(message))
        .isInstanceOf(MessageDeliveryException.class)
        .hasCauseInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(watchingSessionRepository);
  }

  @Test
  void inboundSubscribeDoesNotQueryDatabaseForOtherSubscriptionDestinations() {
    Message<byte[]> watchMessage = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        authentication
    );
    Message<byte[]> directMessage = message(
        StompCommand.SUBSCRIBE,
        "/sub/conversations/%s/direct-messages".formatted(UUID.randomUUID()),
        authentication
    );

    assertThat(inboundChannel.send(watchMessage)).isTrue();
    assertThat(inboundChannel.send(directMessage)).isTrue();
    verifyNoInteractions(watchingSessionRepository);
  }

  @Test
  void inboundSendDoesNotReplaceSubscriptionAuthorization() {
    Message<byte[]> message = message(
        StompCommand.SEND,
        "/pub/contents/%s/chat".formatted(contentId),
        authentication
    );

    assertThat(inboundChannel.send(message)).isTrue();
    verifyNoInteractions(watchingSessionRepository);
  }

  private Message<byte[]> message(
      StompCommand command,
      String destination,
      UsernamePasswordAuthenticationToken user
  ) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setDestination(destination);
    accessor.setUser(user);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
