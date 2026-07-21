package io.mopl.domain.notification.event;

import io.mopl.domain.directmessage.event.DirectMessageSentEvent;
import io.mopl.domain.follow.event.FollowCreatedEvent;
import io.mopl.domain.playlist.event.PlaylistContentAddedEvent;
import io.mopl.domain.playlist.event.PlaylistCreatedEvent;
import io.mopl.domain.playlist.event.PlaylistSubscribedEvent;
import io.mopl.domain.user.event.UserRoleChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mopl.notification", name = "delivery-mode", havingValue = "kafka")
public class KafkaNotificationDomainEventHandler {

  private final NotificationMessageFactory notificationMessageFactory;
  private final KafkaNotificationRequestedEventHandler kafkaNotificationRequestedEventHandler;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void handleFollowCreated(FollowCreatedEvent event) {
    publish(notificationMessageFactory.from(event));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void handlePlaylistSubscribed(PlaylistSubscribedEvent event) {
    publish(notificationMessageFactory.from(event));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void handlePlaylistContentAdded(PlaylistContentAddedEvent event) {
    publish(notificationMessageFactory.from(event));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void handlePlaylistCreated(PlaylistCreatedEvent event) {
    publish(notificationMessageFactory.from(event));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void handleUserRoleChanged(UserRoleChangedEvent event) {
    publish(notificationMessageFactory.from(event));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void handleDirectMessageSent(DirectMessageSentEvent event) {
    publish(notificationMessageFactory.from(event));
  }

  private void publish(Iterable<NotificationRequestedEvent> events) {
    events.forEach(kafkaNotificationRequestedEventHandler::handle);
  }
}
