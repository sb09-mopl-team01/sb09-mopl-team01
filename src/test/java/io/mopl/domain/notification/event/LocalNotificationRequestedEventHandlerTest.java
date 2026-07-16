package io.mopl.domain.notification.event;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalNotificationRequestedEventHandlerTest {

  private final NotificationService notificationService = org.mockito.Mockito.mock(NotificationService.class);
  private final LocalNotificationRequestedEventHandler handler = new LocalNotificationRequestedEventHandler(
      new NotificationMessageFactory(new ObjectMapper()), notificationService);

  @Test
  @DisplayName("LOCAL 모드는 요청 이벤트를 즉시 알림 생성 서비스로 전달한다")
  void handle() {
    UUID receiverId = UUID.randomUUID();

    handler.handle(new NotificationRequestedEvent(
        UUID.randomUUID(), receiverId, "새 알림", "알림 내용", NotificationLevel.INFO));

    verify(notificationService).create(argThat(command ->
        command.receiverId().equals(receiverId)
            && command.title().equals("새 알림")
            && command.content().equals("알림 내용")
            && command.level() == NotificationLevel.INFO
    ));
  }
}
