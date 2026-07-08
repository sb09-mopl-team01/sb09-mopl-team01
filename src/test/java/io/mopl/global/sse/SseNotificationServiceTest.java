package io.mopl.global.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SseNotificationServiceTest {

  private final SseNotificationService sseNotificationService = new SseNotificationService();

  @Test
  @DisplayName("알림 SSE 이벤트 이름은 명세와 동일하게 notifications를 사용한다")
  void notificationEventNameMatchesSpecification() {
    assertThat(SseNotificationService.NOTIFICATION_EVENT_NAME).isEqualTo("notifications");
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
}
