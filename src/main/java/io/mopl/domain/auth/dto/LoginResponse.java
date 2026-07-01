package io.mopl.domain.auth.dto;

import io.mopl.domain.user.dto.data.UserDto;

public record LoginResponse(
    UserDto userDto,
    String accessToken
) {

}
