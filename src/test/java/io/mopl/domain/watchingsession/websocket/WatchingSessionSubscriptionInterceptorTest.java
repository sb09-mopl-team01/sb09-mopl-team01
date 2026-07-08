package io.mopl.domain.watchingsession.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.mopl.domain.user.entity.User;
import io.mopl.domain.watchingsession.service.WatchingSessionService;
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

class WatchingSessionSubscriptionInterceptorTest {

  private final WatchingSessionService watchingSessionService = mock(WatchingSessionService.class);
  private final WatchingSessionSubscriptionRegistry subscriptionRegistry =
      new WatchingSessionSubscriptionRegistry();
  private final WatchingSessionSubscriptionInterceptor interceptor =
      new WatchingSessionSubscriptionInterceptor(watchingSessionService, subscriptionRegistry);

  private UUID watcherId;
  private UUID contentId;
  private UsernamePasswordAuthenticationToken authentication;

  @BeforeEach
  void setUp() {
    watcherId = UUID.randomUUID();
    contentId = UUID.randomUUID();
    User watcher = User.builder()
        .email("watcher@example.com")
        .passwordHash("hash")
        .name("watcher")
        .build();
    ReflectionTestUtils.setField(watcher, "id", watcherId);
    MoplUserDetails userDetails = new MoplUserDetails(watcher);
    authentication = new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );
  }

  @Test
  void preSendStartsWatchingWhenSubscribeWatchTopic() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    );

    Message<?> result = interceptor.preSend(message, null);

    assertThat(result).isEqualTo(message);
    verify(watchingSessionService).startWatchingBySubscription(watcherId, contentId);
  }

  @Test
  void preSendEndsWatchingWhenLastWatchSubscriptionIsRemoved() {
    interceptor.preSend(message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    ), null);
    interceptor.preSend(message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-2",
        authentication
    ), null);

    interceptor.preSend(message(StompCommand.UNSUBSCRIBE, null, "session-1", "sub-1", authentication), null);
    verify(watchingSessionService, never()).endWatchingIfPresent(watcherId, contentId);

    interceptor.preSend(message(StompCommand.UNSUBSCRIBE, null, "session-1", "sub-2", authentication), null);
    verify(watchingSessionService).endWatchingIfPresent(watcherId, contentId);
  }

  @Test
  void preSendEndsWatchingWhenWebSocketDisconnects() {
    interceptor.preSend(message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    ), null);

    interceptor.preSend(message(StompCommand.DISCONNECT, null, "session-1", null, authentication), null);

    verify(watchingSessionService).endWatchingIfPresent(watcherId, contentId);
  }

  @Test
  void preSendIgnoresOtherSubscriptions() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/chat".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    );

    Message<?> result = interceptor.preSend(message, null);

    assertThat(result).isEqualTo(message);
    verifyNoInteractions(watchingSessionService);
  }

  @Test
  void preSendRejectsUnauthenticatedWatchSubscription() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        null
    );

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  void preSendRejectsInvalidContentIdWatchSubscription() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/not-a-uuid/watch",
        "session-1",
        "sub-1",
        authentication
    );

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(AccessDeniedException.class);
  }

  private Message<byte[]> message(
      StompCommand command,
      String destination,
      String sessionId,
      String subscriptionId,
      UsernamePasswordAuthenticationToken authentication
  ) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setDestination(destination);
    accessor.setSessionId(sessionId);
    accessor.setSubscriptionId(subscriptionId);
    accessor.setUser(authentication);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
