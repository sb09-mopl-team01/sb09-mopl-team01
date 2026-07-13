package io.mopl.domain.mail.event;

import io.mopl.domain.mail.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailEventHandler {

  private final MailService mailService;

  @Async
  @EventListener
  public void handleTempPasswordIssuedEvent(TempPasswordIssuedEvent event) {
    mailService.sendTempPasswordEmail(event.userId(), event.tempPassword());
  }
}
