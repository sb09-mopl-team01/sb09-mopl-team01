package io.mopl.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.notification.entity.Notification;
import io.mopl.domain.notification.entity.NotificationLevel;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({
    io.mopl.global.config.AppConfig.class,
    io.mopl.global.config.QueryDslConfig.class
})
@ActiveProfiles("test")
class NotificationRepositoryTest {

  @Autowired
  private NotificationRepository notificationRepository;

  @Test
  @DisplayName("수신자 기준으로 알림 목록을 최신순 조회한다")
  void findByReceiverIdWithCursorDesc() {
    UUID receiverId = UUID.randomUUID();
    UUID anotherReceiverId = UUID.randomUUID();
    Notification first = saveNotification(receiverId, "첫 번째 알림");
    Notification second = saveNotification(receiverId, "두 번째 알림");
    Notification readNotification = saveNotification(receiverId, "읽은 알림");
    readNotification.markAsRead();
    notificationRepository.saveAndFlush(readNotification);
    saveNotification(anotherReceiverId, "다른 사용자 알림");

    List<Notification> result = notificationRepository.findByReceiverIdWithCursorDesc(
        receiverId,
        null,
        null,
        PageRequest.of(0, 10)
    );

    assertThat(result)
        .extracting(Notification::getReceiverId)
        .containsOnly(receiverId);
    assertThat(result)
        .extracting(Notification::getId)
        .containsExactly(second.getId(), first.getId());
  }

  @Test
  @DisplayName("커서 이전 알림만 최신순으로 조회한다")
  void findByReceiverIdWithCursorDescAfterCursor() {
    UUID receiverId = UUID.randomUUID();
    Notification first = saveNotification(receiverId, "첫 번째 알림");
    Notification second = saveNotification(receiverId, "두 번째 알림");
    saveNotification(receiverId, "세 번째 알림");

    List<Notification> result = notificationRepository.findByReceiverIdWithCursorDesc(
        receiverId,
        second.getCreatedAt(),
        second.getId(),
        PageRequest.of(0, 10)
    );

    assertThat(result)
        .extracting(Notification::getId)
        .containsExactly(first.getId());
  }

  @Test
  @DisplayName("커서 이후 알림만 오래된순으로 조회한다")
  void findByReceiverIdWithCursorAscAfterCursor() {
    UUID receiverId = UUID.randomUUID();
    List<Notification> notifications = List.of(
        saveNotification(receiverId, "첫 번째 알림"),
        saveNotification(receiverId, "두 번째 알림"),
        saveNotification(receiverId, "세 번째 알림")
    ).stream()
        .sorted(Comparator
            .comparing((Notification notification) -> notification.getCreatedAt()
                .truncatedTo(ChronoUnit.MICROS))
            .thenComparing(Notification::getId))
        .toList();
    Notification cursor = notifications.get(1);

    List<Notification> result = notificationRepository.findByReceiverIdWithCursorAsc(
        receiverId,
        cursor.getCreatedAt(),
        cursor.getId(),
        PageRequest.of(0, 10)
    );

    assertThat(result)
        .extracting(Notification::getId)
        .containsExactly(notifications.get(2).getId());
  }

  @Test
  @DisplayName("수신자 기준 읽지 않은 알림 수를 조회한다")
  void countByReceiverIdAndReadFalse() {
    UUID receiverId = UUID.randomUUID();
    saveNotification(receiverId, "첫 번째 알림");
    saveNotification(receiverId, "두 번째 알림");
    Notification readNotification = saveNotification(receiverId, "읽은 알림");
    readNotification.markAsRead();
    notificationRepository.saveAndFlush(readNotification);
    saveNotification(UUID.randomUUID(), "다른 사용자 알림");

    long result = notificationRepository.countByReceiverIdAndReadFalse(receiverId);

    assertThat(result).isEqualTo(2);
  }

  @Test
  @DisplayName("읽지 않은 알림만 읽음 상태로 변경한다")
  void markAsReadIfUnread() {
    UUID receiverId = UUID.randomUUID();
    Notification notification = saveNotification(receiverId, "읽음 처리할 알림");

    int firstUpdateCount = notificationRepository.markAsReadIfUnread(
        notification.getId(),
        receiverId
    );
    int duplicateUpdateCount = notificationRepository.markAsReadIfUnread(
        notification.getId(),
        receiverId
    );

    Notification result = notificationRepository.findById(notification.getId()).orElseThrow();
    assertThat(firstUpdateCount).isEqualTo(1);
    assertThat(duplicateUpdateCount).isZero();
    assertThat(result.isRead()).isTrue();
  }

  private Notification saveNotification(UUID receiverId, String title) {
    Notification notification = Notification.create(
        receiverId,
        title,
        "알림 내용 " + Instant.now(),
        NotificationLevel.INFO
    );
    return notificationRepository.saveAndFlush(notification);
  }
}
