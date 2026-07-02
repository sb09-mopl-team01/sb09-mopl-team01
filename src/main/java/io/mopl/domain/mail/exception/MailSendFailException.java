package io.mopl.domain.mail.exception;

import io.mopl.global.exception.ErrorCode;

public class MailSendFailException extends MailException {

  public MailSendFailException() {
    super(ErrorCode.MAIL_SEND_FAIL);
  }
}
