package io.mopl.domain.notification.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpringNotificationEventPublisher implements NotificationEventPublisher {

  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void publish(NotificationReadEvent event) {
    eventPublisher.publishEvent(event);
    log.debug(
        "Spring notification event dispatched. eventType={}, notificationId={}, receiverId={}",
        event.getClass().getSimpleName(),
        event.notificationId(),
        event.receiverId()
    );
  }
}
