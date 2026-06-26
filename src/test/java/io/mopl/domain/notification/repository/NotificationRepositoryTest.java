package io.mopl.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.notification.entity.Notification;
import io.mopl.domain.notification.entity.NotificationLevel;
import java.time.Instant;
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
    saveNotification(receiverId, "첫 번째 알림");
    Notification second = saveNotification(receiverId, "두 번째 알림");
    Notification third = saveNotification(receiverId, "세 번째 알림");

    List<Notification> result = notificationRepository.findByReceiverIdWithCursorAsc(
        receiverId,
        second.getCreatedAt(),
        second.getId(),
        PageRequest.of(0, 10)
    );

    assertThat(result)
        .extracting(Notification::getId)
        .containsExactly(third.getId());
  }

  @Test
  @DisplayName("수신자 기준 총 알림 수를 조회한다")
  void countByReceiverId() {
    UUID receiverId = UUID.randomUUID();
    saveNotification(receiverId, "첫 번째 알림");
    saveNotification(receiverId, "두 번째 알림");
    saveNotification(UUID.randomUUID(), "다른 사용자 알림");

    long result = notificationRepository.countByReceiverId(receiverId);

    assertThat(result).isEqualTo(2);
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
