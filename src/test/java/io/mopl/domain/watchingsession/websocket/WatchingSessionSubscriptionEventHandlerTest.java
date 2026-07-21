package io.mopl.domain.watchingsession.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.user.entity.User;
import io.mopl.domain.watchingsession.realtime.WatchingSessionLeaseStore;
import io.mopl.domain.watchingsession.realtime.WatchingSessionNodeId;
import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.global.security.MoplUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

class WatchingSessionSubscriptionEventHandlerTest {

  private final WatchingSessionService watchingSessionService = mock(WatchingSessionService.class);
  private final WatchingSessionSubscriptionRegistry subscriptionRegistry =
      new WatchingSessionSubscriptionRegistry();
  private final WatchingSessionSubscriptionResolver subscriptionResolver =
      new WatchingSessionSubscriptionResolver();
  private final WatchingSessionLeaseStore leaseStore = mock(WatchingSessionLeaseStore.class);
  private final WatchingSessionNodeId nodeId = new WatchingSessionNodeId("node-a", "");
  private final WatchingSessionSubscriptionEventHandler eventHandler =
      new WatchingSessionSubscriptionEventHandler(
          watchingSessionService,
          subscriptionRegistry,
          subscriptionResolver,
          leaseStore,
          nodeId
      );

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
  void handleSubscribeStartsWatchingAfterSubscriptionEvent() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    );

    when(leaseStore.acquire(new WatchingSessionSubscription(watcherId, contentId), "node-a"))
        .thenReturn(true);

    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message));

    verify(watchingSessionService).startWatchingBySubscription(watcherId, contentId);
  }

  @Test
  void handleUnsubscribeEndsWatchingWhenLastWatchSubscriptionIsRemoved() {
    when(leaseStore.acquire(new WatchingSessionSubscription(watcherId, contentId), "node-a"))
        .thenReturn(true);
    when(leaseStore.release(new WatchingSessionSubscription(watcherId, contentId), "node-a"))
        .thenReturn(true);
    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    )));
    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-2",
        authentication
    )));

    eventHandler.handleUnsubscribe(new SessionUnsubscribeEvent(
        this,
        message(StompCommand.UNSUBSCRIBE, null, "session-1", "sub-1", authentication)
    ));
    verify(watchingSessionService, never()).endWatchingIfPresent(watcherId, contentId);

    eventHandler.handleUnsubscribe(new SessionUnsubscribeEvent(
        this,
        message(StompCommand.UNSUBSCRIBE, null, "session-1", "sub-2", authentication)
    ));
    verify(watchingSessionService).endWatchingIfPresent(watcherId, contentId);
  }

  @Test
  void handleDisconnectEndsWatching() {
    when(leaseStore.acquire(new WatchingSessionSubscription(watcherId, contentId), "node-a"))
        .thenReturn(true);
    when(leaseStore.release(new WatchingSessionSubscription(watcherId, contentId), "node-a"))
        .thenReturn(true);
    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    )));

    eventHandler.handleDisconnect(new SessionDisconnectEvent(
        this,
        message(StompCommand.DISCONNECT, null, "session-1", null, authentication),
        "session-1",
        CloseStatus.NORMAL
    ));

    verify(watchingSessionService).endWatchingIfPresent(watcherId, contentId);
  }

  @Test
  void handleSubscribeIgnoresOtherSubscriptions() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/chat".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    );

    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message));

    verify(watchingSessionService, never()).startWatchingBySubscription(watcherId, contentId);
  }

  @Test
  void handleSubscribeIgnoresInvalidContentIdWatchSubscription() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/not-a-uuid/watch",
        "session-1",
        "sub-1",
        authentication
    );

    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message));

    verify(watchingSessionService, never()).startWatchingBySubscription(any(), any());
  }

  @Test
  void handleSubscribeIgnoresUnauthenticatedWatchSubscription() {
    Message<byte[]> message = message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        null
    );

    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message));

    verify(watchingSessionService, never()).startWatchingBySubscription(any(), any());
  }

  @Test
  void handleSubscribeDoesNotStartWatchingWhenAnotherTaskAlreadyHasLease() {
    when(leaseStore.acquire(new WatchingSessionSubscription(watcherId, contentId), "node-a"))
        .thenReturn(false);

    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    )));

    verify(watchingSessionService, never()).startWatchingBySubscription(watcherId, contentId);
  }

  @Test
  void handleUnsubscribeDoesNotEndWatchingWhenAnotherTaskStillHasLease() {
    when(leaseStore.acquire(new WatchingSessionSubscription(watcherId, contentId), "node-a"))
        .thenReturn(true);
    when(leaseStore.release(new WatchingSessionSubscription(watcherId, contentId), "node-a"))
        .thenReturn(false);
    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    )));

    eventHandler.handleUnsubscribe(new SessionUnsubscribeEvent(
        this,
        message(StompCommand.UNSUBSCRIBE, null, "session-1", "sub-1", authentication)
    ));

    verify(watchingSessionService, never()).endWatchingIfPresent(watcherId, contentId);
  }

  @Test
  void handleSubscribeRollsBackLeaseWhenWatchingSessionStartFails() {
    WatchingSessionSubscription subscription = new WatchingSessionSubscription(watcherId, contentId);
    when(leaseStore.acquire(subscription, "node-a")).thenReturn(true);
    doThrow(new IllegalStateException("database unavailable"))
        .when(watchingSessionService)
        .startWatchingBySubscription(watcherId, contentId);

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message(
            StompCommand.SUBSCRIBE,
            "/sub/contents/%s/watch".formatted(contentId),
            "session-1",
            "sub-1",
            authentication
        )))
    ).isInstanceOf(IllegalStateException.class);

    verify(leaseStore).release(subscription, "node-a");
    verify(watchingSessionService).startWatchingBySubscription(watcherId, contentId);
  }

  @Test
  void handleUnsubscribeRestoresLeaseWhenWatchingSessionEndFails() {
    WatchingSessionSubscription subscription = new WatchingSessionSubscription(watcherId, contentId);
    when(leaseStore.acquire(subscription, "node-a")).thenReturn(true);
    when(leaseStore.release(subscription, "node-a")).thenReturn(true);
    doThrow(new IllegalStateException("database unavailable"))
        .when(watchingSessionService)
        .endWatchingIfPresent(watcherId, contentId);
    eventHandler.handleSubscribe(new SessionSubscribeEvent(this, message(
        StompCommand.SUBSCRIBE,
        "/sub/contents/%s/watch".formatted(contentId),
        "session-1",
        "sub-1",
        authentication
    )));

    eventHandler.handleUnsubscribe(new SessionUnsubscribeEvent(
        this,
        message(StompCommand.UNSUBSCRIBE, null, "session-1", "sub-1", authentication)
    ));

    verify(leaseStore, times(2)).acquire(subscription, "node-a");
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
