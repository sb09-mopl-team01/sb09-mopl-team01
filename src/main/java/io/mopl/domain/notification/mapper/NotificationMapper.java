package io.mopl.domain.notification.mapper;

import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

  NotificationDto toDto(Notification notification);
}
