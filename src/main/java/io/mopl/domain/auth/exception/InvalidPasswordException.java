package io.mopl.domain.auth.exception;

import io.mopl.global.exception.ErrorCode;

public class InvalidPasswordException extends AuthException {

  public InvalidPasswordException() {
    super(ErrorCode.INVALID_PASSWORD);
  }
}
