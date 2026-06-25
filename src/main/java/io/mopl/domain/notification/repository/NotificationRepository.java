package io.mopl.domain.notification.repository;

import io.mopl.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  @Query(value = """
      SELECT *
      FROM notifications
      WHERE receiver_id = :receiverId
        AND (
          :cursor IS NULL
          OR created_at < :cursor
          OR (
            created_at = :cursor
            AND :idAfter IS NOT NULL
            AND CAST(id AS VARCHAR) < CAST(:idAfter AS VARCHAR)
          )
        )
      ORDER BY created_at DESC, CAST(id AS VARCHAR) DESC
      """, nativeQuery = true)
  List<Notification> findByReceiverIdWithCursorDesc(
      @Param("receiverId") UUID receiverId,
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable
  );

  @Query(value = """
      SELECT *
      FROM notifications
      WHERE receiver_id = :receiverId
        AND (
          :cursor IS NULL
          OR created_at > :cursor
          OR (
            created_at = :cursor
            AND :idAfter IS NOT NULL
            AND CAST(id AS VARCHAR) > CAST(:idAfter AS VARCHAR)
          )
        )
      ORDER BY created_at ASC, CAST(id AS VARCHAR) ASC
      """, nativeQuery = true)
  List<Notification> findByReceiverIdWithCursorAsc(
      @Param("receiverId") UUID receiverId,
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable
  );

  long countByReceiverId(UUID receiverId);
}
