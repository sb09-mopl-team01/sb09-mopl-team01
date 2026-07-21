package io.mopl.domain.mail.exception;

import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;

public abstract class MailException extends BaseException {

  public MailException(ErrorCode errorCode) {
    super(errorCode);
  }
}
