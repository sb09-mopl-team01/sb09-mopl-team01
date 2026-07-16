package io.mopl.domain.notification.realtime;

import io.mopl.domain.notification.event.NotificationCreatedEvent;

public interface NotificationRealtimePublisher {

  void publish(NotificationCreatedEvent event);
}
