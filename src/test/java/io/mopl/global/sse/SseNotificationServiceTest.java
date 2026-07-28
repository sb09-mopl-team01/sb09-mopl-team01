package io.mopl.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.global.event.DomainEventPublisher;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SseNotificationServiceTest {

  private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
  private final SseNotificationService sseNotificationService =
      new SseNotificationService(eventPublisher);

  @Test
  @DisplayName("알림 SSE 이벤트 이름은 명세와 동일하게 notifications를 사용한다")
  void notificationEventNameMatchesSpecification() {
    assertThat(SseNotificationService.NOTIFICATION_EVENT_NAME).isEqualTo("notifications");
    assertThat(SseNotificationService.DIRECT_MESSAGE_EVENT_NAME).isEqualTo("direct-messages");
  }

  @Test
  @DisplayName("사용자별 SSE 연결 수를 제한한다")
  void subscribeLimitsEmitterCountPerUser() {
    UUID receiverId = UUID.randomUUID();

    sseNotificationService.subscribe(receiverId, null);
    sseNotificationService.subscribe(receiverId, null);
    sseNotificationService.subscribe(receiverId, null);
    sseNotificationService.subscribe(receiverId, null);

    assertThat(sseNotificationService.countByReceiverId(receiverId)).isEqualTo(3);
  }

  @Test
  @DisplayName("수신자 기준 SSE 연결을 모두 종료한다")
  void closeByReceiverId() {
    UUID receiverId = UUID.randomUUID();
    sseNotificationService.subscribe(receiverId, null);
    sseNotificationService.subscribe(receiverId, null);

    sseNotificationService.closeByReceiverId(receiverId);

    assertThat(sseNotificationService.countByReceiverId(receiverId)).isZero();
  }

  @Test
  @DisplayName("마지막 이벤트 ID가 있어도 SSE 연결을 생성한다")
  void subscribesWithLastEventId() {
    UUID receiverId = UUID.randomUUID();
    UUID lastEventId = UUID.randomUUID();

    sseNotificationService.subscribe(receiverId, lastEventId);

    assertThat(sseNotificationService.countByReceiverId(receiverId)).isOne();
    verify(eventPublisher).publish(argThat(event ->
        event instanceof SseConnectedEvent connectedEvent
            && connectedEvent.receiverId().equals(receiverId)
            && connectedEvent.emitterId() != null
    ));
  }

  @Test
  @DisplayName("알림 동기화 이벤트 발행 실패가 SSE 연결 생성을 중단하지 않는다")
  void isolatesConnectionSyncEventPublishFailure() {
    UUID receiverId = UUID.randomUUID();
    doThrow(new IllegalStateException("event unavailable"))
        .when(eventPublisher)
        .publish(any());

    sseNotificationService.subscribe(receiverId, null);

    assertThat(sseNotificationService.countByReceiverId(receiverId)).isOne();
  }

  @Test
  @DisplayName("연결된 수신자에게 알림과 하트비트를 전송하고 연결이 없는 수신자는 무시한다")
  void sendsNotificationAndHeartbeatOnlyToExistingConnections() {
    UUID receiverId = UUID.randomUUID();
    sseNotificationService.subscribe(receiverId, null);

    sseNotificationService.sendNotification(receiverId, UUID.randomUUID(), "새 알림");
    sseNotificationService.sendDirectMessage(receiverId, UUID.randomUUID(), "새 DM");
    sseNotificationService.sendNotification(UUID.randomUUID(), UUID.randomUUID(), "무시되는 알림");
    sseNotificationService.sendHeartbeat();
    sseNotificationService.closeByReceiverId(UUID.randomUUID());

    assertThat(sseNotificationService.countByReceiverId(receiverId)).isOne();
  }
}
