package io.mopl.domain.auth.exception;

import io.mopl.global.exception.ErrorCode;

public class InvalidEmailException extends AuthException {

  public InvalidEmailException() {
    super(ErrorCode.INVALID_EMAIL);
  }
}
