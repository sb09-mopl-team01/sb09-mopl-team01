package io.mopl.domain.notification.event;

import io.mopl.domain.directmessage.event.DirectMessageSentEvent;
import io.mopl.domain.follow.event.FollowCreatedEvent;
import io.mopl.domain.notification.dto.NotificationCreateCommand;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.service.NotificationService;
import io.mopl.domain.playlist.event.PlaylistSubscribedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class NotificationDomainEventHandler {

  private static final String DEFAULT_DISPLAY_NAME = "사용자";
  private static final String FOLLOW_TITLE = "새 팔로워가 생겼습니다";
  private static final String FOLLOW_CONTENT_FORMAT = "%s님이 회원님을 팔로우했습니다.";
  private static final String PLAYLIST_SUBSCRIBED_TITLE = "플레이리스트를 구독했습니다";
  private static final String PLAYLIST_SUBSCRIBED_CONTENT_FORMAT =
      "%s님이 '%s' 플레이리스트를 구독했습니다.";
  private static final String DIRECT_MESSAGE_TITLE = "새 DM이 도착했습니다";
  private static final String DIRECT_MESSAGE_CONTENT_FORMAT = "%s님이 메시지를 보냈습니다.";

  private final NotificationService notificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleFollowCreated(FollowCreatedEvent event) {
    notificationService.create(new NotificationCreateCommand(
        event.followeeId(),
        FOLLOW_TITLE,
        FOLLOW_CONTENT_FORMAT.formatted(displayName(event.followerName())),
        NotificationLevel.INFO
    ));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePlaylistSubscribed(PlaylistSubscribedEvent event) {
    notificationService.create(new NotificationCreateCommand(
        event.ownerId(),
        PLAYLIST_SUBSCRIBED_TITLE,
        PLAYLIST_SUBSCRIBED_CONTENT_FORMAT.formatted(
            displayName(event.subscriberName()),
            event.playlistTitle()
        ),
        NotificationLevel.INFO
    ));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleDirectMessageSent(DirectMessageSentEvent event) {
    notificationService.create(new NotificationCreateCommand(
        event.receiverId(),
        DIRECT_MESSAGE_TITLE,
        DIRECT_MESSAGE_CONTENT_FORMAT.formatted(displayName(event.senderName())),
        NotificationLevel.INFO
    ));
  }

  private String displayName(String name) {
    return StringUtils.hasText(name) ? name : DEFAULT_DISPLAY_NAME;
  }
}
