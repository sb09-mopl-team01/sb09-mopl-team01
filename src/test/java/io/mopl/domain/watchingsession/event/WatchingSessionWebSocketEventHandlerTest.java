package io.mopl.domain.watchingsession.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.watchingsession.dto.WatchingSessionEventMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class WatchingSessionWebSocketEventHandlerTest {

  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final WatchingSessionWebSocketEventHandler eventHandler =
      new WatchingSessionWebSocketEventHandler(messagingTemplate);

  @Test
  @DisplayName("시청 입장 이벤트를 콘텐츠별 구독 경로로 전송한다")
  void handleEntered() {
    UUID sessionId = UUID.randomUUID();
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    Instant occurredAt = Instant.now();

    eventHandler.handleEntered(new WatchingSessionEnteredEvent(sessionId, watcherId, contentId, occurredAt));

    ArgumentCaptor<WatchingSessionEventMessage> messageCaptor =
        ArgumentCaptor.forClass(WatchingSessionEventMessage.class);
    verify(messagingTemplate).convertAndSend(
        eq("/sub/contents/" + contentId + "/watching-sessions"),
        messageCaptor.capture()
    );
    assertThat(messageCaptor.getValue())
        .isEqualTo(new WatchingSessionEventMessage("ENTERED", sessionId, watcherId, contentId, occurredAt));
  }

  @Test
  @DisplayName("시청 퇴장 이벤트를 콘텐츠별 구독 경로로 전송한다")
  void handleLeft() {
    UUID sessionId = UUID.randomUUID();
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    Instant occurredAt = Instant.now();

    eventHandler.handleLeft(new WatchingSessionLeftEvent(sessionId, watcherId, contentId, occurredAt));

    ArgumentCaptor<WatchingSessionEventMessage> messageCaptor =
        ArgumentCaptor.forClass(WatchingSessionEventMessage.class);
    verify(messagingTemplate).convertAndSend(
        eq("/sub/contents/" + contentId + "/watching-sessions"),
        messageCaptor.capture()
    );
    assertThat(messageCaptor.getValue())
        .isEqualTo(new WatchingSessionEventMessage("LEFT", sessionId, watcherId, contentId, occurredAt));
  }
}
