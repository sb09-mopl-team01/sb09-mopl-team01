package io.mopl.domain.notification.event;

import io.mopl.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mopl.notification", name = "delivery-mode", havingValue = "local", matchIfMissing = true)
public class LocalNotificationRequestedEventHandler {

  private final NotificationMessageFactory notificationMessageFactory;
  private final NotificationService notificationService;

  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(NotificationRequestedEvent event) {
    notificationService.create(notificationMessageFactory.from(event).command());
  }
}
