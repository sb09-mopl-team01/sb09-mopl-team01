package io.mopl.domain.notification.event;

import io.mopl.domain.notification.entity.Notification;
import io.mopl.domain.notification.mapper.NotificationMapper;
import io.mopl.domain.notification.repository.NotificationRepository;
import io.mopl.global.sse.SseConnectedEvent;
import io.mopl.global.sse.SseNotificationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSseConnectionEventHandler {

  private static final int CONNECTION_SYNC_LIMIT = 20;

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;
  private final SseNotificationService sseNotificationService;

  @EventListener
  @Transactional(readOnly = true)
  public void handleConnected(SseConnectedEvent event) {
    if (event == null
        || event.receiverId() == null
        || event.emitterId() == null) {
      return;
    }

    try {
      List<Notification> notifications = findNotificationsToSync(event.receiverId());
      notifications.forEach(notification ->
          sseNotificationService.sendNotificationToConnection(
              event.receiverId(),
              event.emitterId(),
              notification.getId(),
              notificationMapper.toDto(notification)
          )
      );
      log.debug(
          "SSE notification connection sync completed. receiverId={}, emitterId={}, count={}",
          event.receiverId(),
          event.emitterId(),
          notifications.size()
      );
    } catch (RuntimeException e) {
      log.warn(
          "SSE notification connection sync failed. receiverId={}, emitterId={}",
          event.receiverId(),
          event.emitterId(),
          e
      );
    }
  }

  private List<Notification> findNotificationsToSync(UUID receiverId) {
    List<Notification> latestNotifications = new ArrayList<>(
        notificationRepository.findByReceiverIdWithCursorDesc(
            receiverId,
            null,
            null,
            PageRequest.of(0, CONNECTION_SYNC_LIMIT)
        )
    );
    Collections.reverse(latestNotifications);
    return latestNotifications;
  }
}
