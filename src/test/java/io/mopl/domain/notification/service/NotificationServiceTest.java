package io.mopl.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import io.mopl.domain.notification.dto.NotificationCreateCommand;
import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.Notification;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.event.NotificationEventPublisher;
import io.mopl.domain.notification.event.NotificationReadEvent;
import io.mopl.domain.notification.repository.NotificationRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest {

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private NotificationRepository notificationRepository;

  @MockitoBean
  private NotificationEventPublisher eventPublisher;

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

  @Test
  @DisplayName("수신자 기준으로 알림 목록을 최신순 커서 응답으로 조회한다")
  void getNotificationsByReceiverId() {
    UUID receiverId = UUID.randomUUID();
    UUID anotherReceiverId = UUID.randomUUID();
    createNotification(receiverId, "첫 번째 알림");
    createNotification(receiverId, "두 번째 알림");
    createNotification(receiverId, "세 번째 알림");
    createNotification(anotherReceiverId, "다른 사용자 알림");

    CursorResponse<NotificationDto> result = notificationService.getNotifications(
        receiverId,
        null,
        null,
        2,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(result.data()).hasSize(2);
    assertThat(result.data())
        .extracting(NotificationDto::receiverId)
        .containsOnly(receiverId);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isNotBlank();
    assertThat(result.nextIdAfter()).isNotNull();
    assertThat(result.totalCount()).isEqualTo(3);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);
  }

  @Test
  @DisplayName("다음 커서로 남은 알림 목록을 이어서 조회한다")
  void getNotificationsWithCursor() {
    UUID receiverId = UUID.randomUUID();
    createNotification(receiverId, "첫 번째 알림");
    createNotification(receiverId, "두 번째 알림");
    createNotification(receiverId, "세 번째 알림");

    CursorResponse<NotificationDto> firstPage = notificationService.getNotifications(
        receiverId,
        null,
        null,
        2,
        "createdAt",
        SortDirection.DESCENDING
    );

    CursorResponse<NotificationDto> secondPage = notificationService.getNotifications(
        receiverId,
        firstPage.nextCursor(),
        firstPage.nextIdAfter(),
        2,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(secondPage.data()).hasSize(1);
    assertThat(secondPage.hasNext()).isFalse();
    assertThat(secondPage.nextCursor()).isNull();
    assertThat(secondPage.nextIdAfter()).isNull();
    assertThat(secondPage.totalCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("알림을 읽음 상태로 변경한다")
  void readNotification() {
    NotificationDto notification = createNotification(UUID.randomUUID(), "읽음 처리할 알림");

    notificationService.readNotification(notification.id());

    Notification result = notificationRepository.findById(notification.id()).orElseThrow();
    assertThat(result.isRead()).isTrue();
    verify(eventPublisher).publish(argThat(event ->
        event.notificationId().equals(notification.id())
            && event.receiverId().equals(notification.receiverId())
            && event.occurredAt() != null
    ));
  }

  @Test
  @DisplayName("이미 읽은 알림을 다시 읽음 처리해도 성공한다")
  void readNotificationAlreadyRead() {
    NotificationDto notification = createNotification(UUID.randomUUID(), "이미 읽은 알림");
    notificationService.readNotification(notification.id());

    notificationService.readNotification(notification.id());

    Notification result = notificationRepository.findById(notification.id()).orElseThrow();
    assertThat(result.isRead()).isTrue();
  }

  @Test
  @DisplayName("존재하지 않는 알림은 읽음 처리하지 않는다")
  void readNotificationNotFound() {
    assertThatThrownBy(() -> notificationService.readNotification(UUID.randomUUID()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  @DisplayName("목록 조회 요청 값이 유효하지 않으면 조회하지 않는다")
  void getNotificationsWithInvalidCommand() {
    assertThatThrownBy(() -> notificationService.getNotifications(
        UUID.randomUUID(),
        "invalid-cursor",
        UUID.randomUUID(),
        10,
        "createdAt",
        SortDirection.DESCENDING
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);

    assertThatThrownBy(() -> notificationService.getNotifications(
        UUID.randomUUID(),
        null,
        null,
        10,
        "title",
        SortDirection.DESCENDING
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT);
  }

  private NotificationDto createNotification(UUID receiverId, String title) {
    return notificationService.create(new NotificationCreateCommand(
        receiverId,
        title,
        "알림 내용",
        NotificationLevel.INFO
    ));
  }
}
