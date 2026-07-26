package io.mopl.domain.notification.repository;

import io.mopl.domain.notification.entity.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends
    JpaRepository<Notification, UUID>,
    NotificationRepositoryCustom {

  long countByReceiverId(UUID receiverId);

  long countByReceiverIdAndReadFalse(UUID receiverId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Notification notification
      set notification.read = true
      where notification.id = :notificationId
        and notification.receiverId = :receiverId
        and notification.read = false
      """)
  int markAsReadIfUnread(
      @Param("notificationId") UUID notificationId,
      @Param("receiverId") UUID receiverId
  );
}
