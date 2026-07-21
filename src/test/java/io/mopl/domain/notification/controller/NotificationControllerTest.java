package io.mopl.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.service.NotificationService;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import io.mopl.global.security.MoplUserDetails;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

  @Mock
  private NotificationService notificationService;

  @Test
  @DisplayName("GET /api/notifications - 요청자의 알림 목록 조회")
  void getNotifications() {
    UUID receiverId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    String cursor = "2026-07-02T10:15:30Z";
    MoplUserDetails userDetails = userDetails(receiverId);
    NotificationDto notification = new NotificationDto(
        UUID.randomUUID(),
        Instant.parse("2026-07-02T10:16:00Z"),
        receiverId,
        "새 알림",
        "알림 내용",
        NotificationLevel.INFO,
        false
    );
    CursorResponse<NotificationDto> response = new CursorResponse<>(
        List.of(notification),
        "2026-07-02T10:16:00Z",
        notification.id(),
        true,
        2,
        "createdAt",
        SortDirection.DESCENDING
    );
    given(notificationService.getNotifications(
        eq(receiverId),
        eq(cursor),
        eq(idAfter),
        eq(10),
        eq("createdAt"),
        eq(SortDirection.DESCENDING)
    )).willReturn(response);
    NotificationController notificationController = new NotificationController(notificationService);

    ResponseEntity<CursorResponse<NotificationDto>> result = notificationController.getNotifications(
        userDetails,
        cursor,
        idAfter,
        10,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(result.getBody()).isEqualTo(response);
    verify(notificationService).getNotifications(
        receiverId,
        cursor,
        idAfter,
        10,
        "createdAt",
        SortDirection.DESCENDING
    );
  }

  @Test
  @DisplayName("DELETE /api/notifications/{notificationId} - 알림 읽음 처리")
  void readNotification() {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    NotificationController notificationController = new NotificationController(notificationService);

    ResponseEntity<Void> result = notificationController.readNotification(
        userDetails(receiverId),
        notificationId
    );

    assertThat(result.getStatusCode().value()).isEqualTo(204);
    verify(notificationService).readNotification(notificationId, receiverId);
  }

  @Test
  @DisplayName("인증 사용자의 User 정보가 없으면 알림 목록을 조회하지 않는다")
  void getNotificationsRequiresUserInPrincipal() {
    NotificationController notificationController = new NotificationController(notificationService);

    assertThatThrownBy(() -> notificationController.getNotifications(
        new MoplUserDetails(null),
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
  }

  @Test
  @DisplayName("인증 사용자의 ID가 없으면 알림을 읽음 처리하지 않는다")
  void readNotificationRequiresUserIdInPrincipal() {
    NotificationController notificationController = new NotificationController(notificationService);
    User user = User.builder()
        .email("user-without-id@example.com")
        .passwordHash("password")
        .name("사용자")
        .role(Role.USER)
        .build();

    assertThatThrownBy(() -> notificationController.readNotification(
        new MoplUserDetails(user),
        UUID.randomUUID()
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
  }

  private MoplUserDetails userDetails(UUID userId) {
    User user = User.builder()
        .email("user@example.com")
        .passwordHash("password")
        .name("사용자")
        .role(Role.USER)
        .build();
    ReflectionTestUtils.setField(user, "id", userId);
    return new MoplUserDetails(user);
  }
}
