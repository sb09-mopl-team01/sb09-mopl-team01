package io.mopl.domain.user.exception;

import io.mopl.global.exception.ErrorCode;

public class DuplicateUserEmailException extends UserException{

  public DuplicateUserEmailException() {
    super(ErrorCode.EMAIL_DUPLICATION);
  }
}
