package io.mopl.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.Notification;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.mapper.NotificationMapper;
import io.mopl.domain.notification.repository.NotificationRepository;
import io.mopl.global.sse.SseConnectedEvent;
import io.mopl.global.sse.SseNotificationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationSseConnectionEventHandlerTest {

  private static final int CONNECTION_SYNC_LIMIT = 20;

  private final NotificationRepository notificationRepository =
      mock(NotificationRepository.class);
  private final NotificationMapper notificationMapper = mock(NotificationMapper.class);
  private final SseNotificationService sseNotificationService =
      mock(SseNotificationService.class);
  private final NotificationSseConnectionEventHandler handler =
      new NotificationSseConnectionEventHandler(
          notificationRepository,
          notificationMapper,
          sseNotificationService
      );

  @Test
  void replaysLatestUnreadNotificationsInChronologicalOrderWhenSseConnects() {
    UUID receiverId = UUID.randomUUID();
    UUID emitterId = UUID.randomUUID();
    Notification olderNotification = notification(
        receiverId,
        Instant.parse("2026-07-28T00:00:00Z")
    );
    Notification newerNotification = notification(
        receiverId,
        Instant.parse("2026-07-28T00:01:00Z")
    );
    NotificationDto olderDto = dto(olderNotification);
    NotificationDto newerDto = dto(newerNotification);
    when(notificationRepository.findByReceiverIdWithCursorDesc(
        receiverId,
        null,
        null,
        PageRequest.of(0, CONNECTION_SYNC_LIMIT)
    )).thenReturn(List.of(newerNotification, olderNotification));
    when(notificationMapper.toDto(olderNotification)).thenReturn(olderDto);
    when(notificationMapper.toDto(newerNotification)).thenReturn(newerDto);

    handler.handleConnected(new SseConnectedEvent(receiverId, emitterId));

    InOrder inOrder = inOrder(sseNotificationService);
    inOrder.verify(sseNotificationService).sendNotificationToConnection(
        receiverId,
        emitterId,
        olderNotification.getId(),
        olderDto
    );
    inOrder.verify(sseNotificationService).sendNotificationToConnection(
        receiverId,
        emitterId,
        newerNotification.getId(),
        newerDto
    );
  }

  @Test
  void isolatesReplayRepositoryFailureFromSseConnection() {
    UUID receiverId = UUID.randomUUID();
    UUID emitterId = UUID.randomUUID();
    when(notificationRepository.findByReceiverIdWithCursorDesc(
        receiverId,
        null,
        null,
        PageRequest.of(0, CONNECTION_SYNC_LIMIT)
    ))
        .thenThrow(new IllegalStateException("database unavailable"));

    assertThatCode(() -> handler.handleConnected(
        new SseConnectedEvent(receiverId, emitterId)
    )).doesNotThrowAnyException();

    verify(sseNotificationService, never())
        .sendNotificationToConnection(any(), any(), any(), any());
  }

  private Notification notification(UUID receiverId, Instant createdAt) {
    Notification notification = Notification.create(
        receiverId,
        "알림",
        "알림 내용",
        NotificationLevel.INFO
    );
    ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(notification, "createdAt", createdAt);
    return notification;
  }

  private NotificationDto dto(Notification notification) {
    return new NotificationDto(
        notification.getId(),
        notification.getCreatedAt(),
        notification.getReceiverId(),
        notification.getTitle(),
        notification.getContent(),
        notification.getLevel(),
        notification.isRead()
    );
  }
}
