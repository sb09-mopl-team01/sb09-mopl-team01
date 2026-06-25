package io.mopl.domain.notification.service;

import io.mopl.domain.notification.dto.NotificationCreateCommand;
import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.Notification;
import io.mopl.domain.notification.mapper.NotificationMapper;
import io.mopl.domain.notification.repository.NotificationRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NotificationService {

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

  private void validateCreateCommand(NotificationCreateCommand command) {
    if (command == null
        || command.receiverId() == null
        || !StringUtils.hasText(command.title())
        || !StringUtils.hasText(command.content())
        || command.level() == null) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }
}
