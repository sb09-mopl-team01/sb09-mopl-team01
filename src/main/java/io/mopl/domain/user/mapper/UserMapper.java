package io.mopl.domain.user.mapper;

import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  UserDto toDto(User user);

  @Mapping(source = "password", target = "passwordHash")
  User toEntity(UserCreateRequest request);
}
