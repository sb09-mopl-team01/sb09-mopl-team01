package io.mopl.domain.auth.dto;

import io.mopl.domain.user.dto.data.UserDto;

public record TokenRefreshResult(
    String newAccessToken,
    String newRefreshToken,
    UserDto userDto
) {

}
