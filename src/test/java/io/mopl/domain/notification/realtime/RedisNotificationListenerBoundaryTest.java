package io.mopl.domain.notification.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.notification.event.NotificationCreatedEvent;
import io.mopl.global.sse.SseNotificationService;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

class RedisNotificationListenerBoundaryTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final SseNotificationService firstInstanceSseService = mock(SseNotificationService.class);
  private final SseNotificationService secondInstanceSseService = mock(SseNotificationService.class);
  private final RedisNotificationListener firstInstanceListener =
      new RedisNotificationListener(objectMapper, firstInstanceSseService);
  private final RedisNotificationListener secondInstanceListener =
      new RedisNotificationListener(objectMapper, secondInstanceSseService);

  @Test
  void relaysOneRedisMessageToEachInstanceLocalSseConnectionsForTheSameReceiver() throws Exception {
    NotificationCreatedEvent event = NotificationRealtimeFixtures.event();
    DefaultMessage message = message(objectMapper.writeValueAsString(event));

    firstInstanceListener.onMessage(message, null);
    secondInstanceListener.onMessage(message, null);

    verify(firstInstanceSseService).sendNotification(
        eq(event.receiverId()),
        eq(event.notificationId()),
        eq(event.notification())
    );
    verify(secondInstanceSseService).sendNotification(
        eq(event.receiverId()),
        eq(event.notificationId()),
        eq(event.notification())
    );
  }

  @Test
  void ignoresMalformedRedisMessageWithoutSendingToAnyInstanceSseConnections() {
    DefaultMessage message = message("not-json");

    firstInstanceListener.onMessage(message, null);
    secondInstanceListener.onMessage(message, null);

    verifyNoInteractions(firstInstanceSseService, secondInstanceSseService);
  }

  @Test
  void isolatesLocalSseRelayFailureFromRedisListenerContainer() throws Exception {
    NotificationCreatedEvent event = NotificationRealtimeFixtures.event();
    doThrow(new IllegalStateException("SSE connection unavailable"))
        .when(firstInstanceSseService)
        .sendNotification(event.receiverId(), event.notificationId(), event.notification());

    Assertions.assertThatCode(() -> firstInstanceListener.onMessage(
        message(objectMapper.writeValueAsString(event)),
        null
    )).doesNotThrowAnyException();
  }

  @Test
  void ignoresEmptyAndIncompleteRedisMessages() throws Exception {
    firstInstanceListener.onMessage(null, null);
    firstInstanceListener.onMessage(new DefaultMessage(
        "notification:realtime".getBytes(StandardCharsets.UTF_8),
        new byte[0]
    ), null);
    firstInstanceListener.onMessage(message("{}"), null);

    verifyNoInteractions(firstInstanceSseService);
  }

  private DefaultMessage message(String payload) {
    return new DefaultMessage(
        "notification:realtime".getBytes(StandardCharsets.UTF_8),
        payload.getBytes(StandardCharsets.UTF_8)
    );
  }
}
