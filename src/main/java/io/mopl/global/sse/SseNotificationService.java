package io.mopl.global.sse;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
public class SseNotificationService {

  private static final long DEFAULT_TIMEOUT_MILLIS = 60L * 60L * 1000L;
  private static final int MAX_EMITTERS_PER_USER = 3;
  private static final String CONNECT_EVENT_NAME = "connect";
  static final String NOTIFICATION_EVENT_NAME = "notifications";
  private static final String HEARTBEAT_EVENT_NAME = "ping";

  private final Map<UUID, Map<UUID, SseConnection>> emitters = new ConcurrentHashMap<>();

  public SseEmitter subscribe(UUID receiverId, UUID lastEventId) {
    SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MILLIS);
    UUID emitterId = UUID.randomUUID();
    Map<UUID, SseConnection> userEmitters =
        emitters.computeIfAbsent(receiverId, ignored -> new ConcurrentHashMap<>());

    closeOldestIfLimitExceeded(receiverId, userEmitters);
    userEmitters.put(emitterId, new SseConnection(emitter, Instant.now()));

    emitter.onCompletion(() -> remove(receiverId, emitterId));
    emitter.onTimeout(() -> remove(receiverId, emitterId));
    emitter.onError(error -> remove(receiverId, emitterId));

    if (lastEventId != null) {
      log.debug("SSE reconnect requested. receiverId={}, lastEventId={}", receiverId, lastEventId);
    }

    try {
      emitter.send(SseEmitter.event()
          .id(emitterId.toString())
          .name(CONNECT_EVENT_NAME)
          .data("connected"));
    } catch (Exception e) {
      closeQuietly(receiverId, emitterId, emitter, e);
    }

    return emitter;
  }

  public void sendNotification(UUID receiverId, UUID notificationId, Object notification) {
    Map<UUID, SseConnection> userEmitters = emitters.get(receiverId);
    if (userEmitters == null || userEmitters.isEmpty()) {
      return;
    }

    userEmitters.forEach((emitterId, connection) -> {
      try {
        connection.emitter().send(SseEmitter.event()
            .id(notificationId.toString())
            .name(NOTIFICATION_EVENT_NAME)
            .data(notification));
      } catch (Exception e) {
        closeQuietly(receiverId, emitterId, connection.emitter(), e);
      }
    });
  }

  @Scheduled(fixedDelayString = "${sse.heartbeat-delay-millis:30000}")
  public void sendHeartbeat() {
    emitters.forEach((receiverId, userEmitters) ->
        userEmitters.forEach((emitterId, connection) -> {
          try {
            connection.emitter().send(SseEmitter.event()
                .name(HEARTBEAT_EVENT_NAME)
                .data("ping"));
          } catch (Exception e) {
            closeQuietly(receiverId, emitterId, connection.emitter(), e);
          }
        })
    );
  }

  public void closeByReceiverId(UUID receiverId) {
    Map<UUID, SseConnection> userEmitters = emitters.remove(receiverId);
    if (userEmitters == null) {
      return;
    }

    userEmitters.values().forEach(connection -> safeComplete(connection.emitter()));
  }

  int countByReceiverId(UUID receiverId) {
    Map<UUID, SseConnection> userEmitters = emitters.get(receiverId);
    return userEmitters == null ? 0 : userEmitters.size();
  }

  private void closeOldestIfLimitExceeded(UUID receiverId, Map<UUID, SseConnection> userEmitters) {
    if (userEmitters.size() < MAX_EMITTERS_PER_USER) {
      return;
    }

    Optional<Map.Entry<UUID, SseConnection>> oldestConnection = userEmitters.entrySet().stream()
        .min(Comparator.comparing(entry -> entry.getValue().connectedAt()));
    oldestConnection.ifPresent(entry -> {
      remove(receiverId, entry.getKey());
      safeComplete(entry.getValue().emitter());
    });
  }

  private void closeQuietly(
      UUID receiverId,
      UUID emitterId,
      SseEmitter emitter,
      Exception exception
  ) {
    remove(receiverId, emitterId);
    if (isExpectedDisconnect(exception)) {
      log.debug(
          "SSE connection closed. receiverId={}, emitterId={}, reason={}",
          receiverId,
          emitterId,
          exception.getClass().getSimpleName()
      );
    } else {
      log.warn(
          "SSE send failed unexpectedly. receiverId={}, emitterId={}",
          receiverId,
          emitterId,
          exception
      );
    }
    safeComplete(emitter);
  }

  private boolean isExpectedDisconnect(Exception exception) {
    return exception instanceof IOException
        || exception instanceof IllegalStateException
        || exception instanceof AsyncRequestNotUsableException;
  }

  private void safeComplete(SseEmitter emitter) {
    try {
      emitter.complete();
    } catch (Exception e) {
      log.trace("SSE emitter already completed.", e);
    }
  }

  private void remove(UUID receiverId, UUID emitterId) {
    emitters.computeIfPresent(receiverId, (id, userEmitters) -> {
      userEmitters.remove(emitterId);
      return userEmitters.isEmpty() ? null : userEmitters;
    });
  }

  private record SseConnection(
      SseEmitter emitter,
      Instant connectedAt
  ) {
  }
}
