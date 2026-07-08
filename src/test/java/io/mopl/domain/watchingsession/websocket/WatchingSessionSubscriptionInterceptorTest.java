package io.mopl.domain.watchingsession.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class WatchingSessionSubscriptionInterceptorTest {

  private final WatchingSessionSubscriptionInterceptor interceptor =
      new WatchingSessionSubscriptionInterceptor();

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
  void preSendAllowsAuthenticatedWatchTopicSubscription() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    );

    Message<?> result = interceptor.preSend(message, null);

    assertThat(result).isEqualTo(message);
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
