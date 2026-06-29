package io.mopl.domain.notification.service;

import io.mopl.domain.notification.dto.NotificationCreateCommand;
import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.Notification;
import io.mopl.domain.notification.mapper.NotificationMapper;
import io.mopl.domain.notification.repository.NotificationRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.logging.CursorPageLogger;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

  private static final String NOTIFICATION_DOMAIN = "notification";
  private static final String CREATED_AT_SORT = "createdAt";

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;

  @Transactional
  public NotificationDto create(NotificationCreateCommand command) {
    validateCreateCommand(command);

    Notification notification = Notification.create(
        command.receiverId(),
        command.title(),
        command.content(),
        command.level()
    );

    return notificationMapper.toDto(notificationRepository.save(notification));
  }

  @Transactional(readOnly = true)
  public CursorResponse<NotificationDto> getNotifications(
      UUID receiverId,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      SortDirection sortDirection
  ) {
    validateListCommand(receiverId, cursor, idAfter, limit, sortBy, sortDirection);

    Instant parsedCursor = parseCursor(cursor);
    CursorPageLogger.logNextPageRequest(
        NOTIFICATION_DOMAIN,
        receiverId,
        parsedCursor,
        idAfter,
        limit,
        sortDirection
    );

    List<Notification> notifications = findNotifications(
        receiverId,
        parsedCursor,
        idAfter,
        limit,
        sortDirection
    );

    boolean hasNext = notifications.size() > limit;
    List<Notification> pageData = notifications.stream()
        .limit(limit)
        .toList();
    List<NotificationDto> data = pageData.stream()
        .map(notificationMapper::toDto)
        .toList();
    Notification lastNotification = pageData.isEmpty() ? null : pageData.get(pageData.size() - 1);
    CursorPageLogger.logNextPageResult(
        NOTIFICATION_DOMAIN,
        receiverId,
        parsedCursor,
        idAfter,
        pageData.size(),
        hasNext,
        hasNext && lastNotification != null ? lastNotification.getCreatedAt() : null,
        hasNext && lastNotification != null ? lastNotification.getId() : null
    );

    return new CursorResponse<>(
        data,
        hasNext && lastNotification != null ? lastNotification.getCreatedAt().toString() : null,
        hasNext && lastNotification != null ? lastNotification.getId() : null,
        hasNext,
        notificationRepository.countByReceiverId(receiverId),
        sortBy,
        sortDirection
    );
  }

  private void validateCreateCommand(NotificationCreateCommand command) {
    if (command == null
        || command.receiverId() == null
        || !StringUtils.hasText(command.title())
        || !StringUtils.hasText(command.content())
        || command.level() == null) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private void validateListCommand(
      UUID receiverId,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      SortDirection sortDirection
  ) {
    if (receiverId == null
        || limit <= 0
        || !CREATED_AT_SORT.equals(sortBy)
        || sortDirection == null) {
      log.warn("Invalid notification list request. receiverId={}, limit={}, sortBy={}, sortDirection={}",
          receiverId, limit, sortBy, sortDirection);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private Instant parseCursor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }

    try {
      return Instant.parse(cursor);
    } catch (DateTimeException e) {
      log.warn("Invalid notification cursor format. cursor={}", cursor);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private List<Notification> findNotifications(
      UUID receiverId,
      Instant cursor,
      UUID idAfter,
      int limit,
      SortDirection sortDirection
  ) {
    PageRequest pageRequest = PageRequest.of(0, limit + 1);
    if (sortDirection == SortDirection.ASCENDING) {
      return notificationRepository.findByReceiverIdWithCursorAsc(
          receiverId,
          cursor,
          idAfter,
          pageRequest
      );
    }

    return notificationRepository.findByReceiverIdWithCursorDesc(
        receiverId,
        cursor,
        idAfter,
        pageRequest
    );
  }
}
