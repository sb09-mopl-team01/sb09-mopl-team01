package io.mopl.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mopl.domain.notification.dto.NotificationCreateCommand;
import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.Notification;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.repository.NotificationRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest {

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private NotificationRepository notificationRepository;

  @Test
  @DisplayName("알림 생성 요청을 저장하고 DTO로 반환한다")
  void createNotification() {
    UUID receiverId = UUID.randomUUID();
    NotificationCreateCommand command = new NotificationCreateCommand(
        receiverId,
        "새 DM이 도착했습니다",
        "상대방이 메시지를 보냈습니다.",
        NotificationLevel.INFO
    );

    NotificationDto result = notificationService.create(command);

    assertThat(result.id()).isNotNull();
    assertThat(result.createdAt()).isNotNull();
    assertThat(result.receiverId()).isEqualTo(receiverId);
    assertThat(result.title()).isEqualTo("새 DM이 도착했습니다");
    assertThat(result.content()).isEqualTo("상대방이 메시지를 보냈습니다.");
    assertThat(result.level()).isEqualTo(NotificationLevel.INFO);

    Notification savedNotification = notificationRepository.findById(result.id()).orElseThrow();
    assertThat(savedNotification.getReceiverId()).isEqualTo(receiverId);
    assertThat(savedNotification.isRead()).isFalse();
  }

  @Test
  @DisplayName("필수 값이 비어 있으면 알림을 생성하지 않는다")
  void createNotificationWithInvalidCommand() {
    NotificationCreateCommand command = new NotificationCreateCommand(
        UUID.randomUUID(),
        "",
        "상대방이 메시지를 보냈습니다.",
        NotificationLevel.INFO
    );

    assertThatThrownBy(() -> notificationService.create(command))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }
}
