package io.mopl.domain.watchingsession.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.watchingsession.dto.WatchingSessionChange;
import io.mopl.domain.watchingsession.dto.WatchingSessionChangeType;
import io.mopl.domain.watchingsession.dto.WatchingSessionDto;
import io.mopl.domain.watchingsession.realtime.WatchingSessionPresenceStore;
import io.mopl.domain.watchingsession.realtime.WatchingSessionRealtimePublisher;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WatchingSessionWebSocketEventHandlerTest {

  private final WatchingSessionPresenceStore presenceStore = mock(WatchingSessionPresenceStore.class);
  private final WatchingSessionRealtimePublisher realtimePublisher = mock(WatchingSessionRealtimePublisher.class);
  private final WatchingSessionWebSocketEventHandler eventHandler =
      new WatchingSessionWebSocketEventHandler(presenceStore, realtimePublisher);

  @Test
  @DisplayName("시청 입장 이벤트를 Redis 공유 상태와 실시간 중계로 전달한다")
  void handleEntered() {
    UUID sessionId = UUID.randomUUID();
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    WatchingSessionDto watchingSession = watchingSession(sessionId, watcherId, contentId);
    Instant occurredAt = Instant.now();

    eventHandler.handleEntered(new WatchingSessionEnteredEvent(watchingSession, 3L, occurredAt));

    verify(presenceStore).enter(watcherId, contentId);
    verify(realtimePublisher).publish(new WatchingSessionChange(
        WatchingSessionChangeType.JOIN,
        watchingSession,
        3L
    ));
  }

  @Test
  @DisplayName("시청 퇴장 이벤트를 Redis 공유 상태와 실시간 중계로 전달한다")
  void handleLeft() {
    UUID sessionId = UUID.randomUUID();
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    WatchingSessionDto watchingSession = watchingSession(sessionId, watcherId, contentId);
    Instant occurredAt = Instant.now();

    eventHandler.handleLeft(new WatchingSessionLeftEvent(watchingSession, 2L, occurredAt));

    verify(presenceStore).leave(watcherId, contentId);
    verify(realtimePublisher).publish(new WatchingSessionChange(
        WatchingSessionChangeType.LEAVE,
        watchingSession,
        2L
    ));
  }

  private WatchingSessionDto watchingSession(UUID sessionId, UUID watcherId, UUID contentId) {
    return new WatchingSessionDto(
        sessionId,
        Instant.now(),
        new UserSummary(watcherId, "사용자", "https://example.com/profile.png"),
        ContentSummary.builder()
            .id(contentId)
            .type(ContentType.MOVIE)
            .title("콘텐츠")
            .description("설명")
            .thumbnailUrl("https://example.com/thumbnail.png")
            .tags(Set.of("tag"))
            .averageRating(4.5)
            .reviewCount(10)
            .build()
    );
  }
}
