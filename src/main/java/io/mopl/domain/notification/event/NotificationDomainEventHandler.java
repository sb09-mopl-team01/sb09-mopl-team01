package io.mopl.domain.notification.event;

import io.mopl.domain.directmessage.event.DirectMessageSentEvent;
import io.mopl.domain.follow.event.FollowCreatedEvent;
import io.mopl.domain.notification.dto.NotificationCreateCommand;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.service.NotificationService;
import io.mopl.domain.playlist.event.PlaylistSubscribedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class NotificationDomainEventHandler {

  private final NotificationService notificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleFollowCreated(FollowCreatedEvent event) {
    notificationService.create(new NotificationCreateCommand(
        event.followeeId(),
        "새 팔로워가 생겼습니다",
        displayName(event.followerName()) + "님이 회원님을 팔로우했습니다.",
        NotificationLevel.INFO
    ));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePlaylistSubscribed(PlaylistSubscribedEvent event) {
    notificationService.create(new NotificationCreateCommand(
        event.ownerId(),
        "플레이리스트를 구독했습니다",
        displayName(event.subscriberName()) + "님이 '" + event.playlistTitle() + "' 플레이리스트를 구독했습니다.",
        NotificationLevel.INFO
    ));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleDirectMessageSent(DirectMessageSentEvent event) {
    notificationService.create(new NotificationCreateCommand(
        event.receiverId(),
        "새 DM이 도착했습니다",
        displayName(event.senderName()) + "님이 메시지를 보냈습니다.",
        NotificationLevel.INFO
    ));
  }

  private String displayName(String name) {
    return StringUtils.hasText(name) ? name : "사용자";
  }
}
