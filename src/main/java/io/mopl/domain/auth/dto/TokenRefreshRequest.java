package io.mopl.domain.auth.dto;

import io.mopl.domain.user.dto.data.UserDto;

public record TokenRefreshRequest(
    UserDto userDto,
    String accessToken
) {

}
