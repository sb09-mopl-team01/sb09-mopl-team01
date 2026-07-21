package io.mopl.domain.user.exception;

import io.mopl.global.exception.ErrorCode;

public class UserNotFoundException extends UserException {
  public UserNotFoundException() {
    super(ErrorCode.USER_NOT_FOUND);
  }
}
