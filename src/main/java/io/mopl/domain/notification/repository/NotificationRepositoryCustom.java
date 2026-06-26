package io.mopl.domain.notification.repository;

import io.mopl.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface NotificationRepositoryCustom {

  List<Notification> findByReceiverIdWithCursorDesc(
      UUID receiverId,
      Instant cursor,
      UUID idAfter,
      Pageable pageable
  );

  List<Notification> findByReceiverIdWithCursorAsc(
      UUID receiverId,
      Instant cursor,
      UUID idAfter,
      Pageable pageable
  );
}
