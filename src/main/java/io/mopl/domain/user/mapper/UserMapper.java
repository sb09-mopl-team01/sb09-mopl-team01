package io.mopl.domain.user.mapper;

import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  @Mapping(target = "currentWatchingContent", ignore = true)
  UserDto toDto(User user);

  default UserDto toDto(User user, ContentSummary currentWatchingContent) {
    return UserDto.builder()
        .id(user.getId())
        .email(user.getEmail())
        .name(user.getName())
        .profileImageUrl(user.getProfileImageUrl())
        .role(user.getRole())
        .locked(user.isLocked())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .currentWatchingContent(currentWatchingContent)
        .build();
  }

  @Mapping(source = "password", target = "passwordHash")
  User toEntity(UserCreateRequest request);
}
