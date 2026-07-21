package io.mopl.domain.auth.exception;

import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;

public abstract class AuthException extends BaseException {

  public AuthException(ErrorCode errorCode) {
    super(errorCode);
  }
}