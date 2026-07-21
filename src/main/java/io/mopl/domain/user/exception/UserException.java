package io.mopl.domain.user.exception;

import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;

public abstract class UserException extends BaseException {

  public UserException(ErrorCode errorCode) {
    super(errorCode);
  }
}
