package io.mopl.domain.notification.event;

import io.mopl.domain.directmessage.event.DirectMessageSentEvent;
import io.mopl.domain.follow.event.FollowCreatedEvent;
import io.mopl.domain.notification.dto.NotificationCreateCommand;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.service.NotificationService;
import io.mopl.domain.playlist.event.PlaylistContentAddedEvent;
import io.mopl.domain.playlist.event.PlaylistCreatedEvent;
import io.mopl.domain.playlist.event.PlaylistSubscribedEvent;
import io.mopl.domain.user.event.UserRoleChangedEvent;
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
  private static final String PLAYLIST_CONTENT_ADDED_TITLE = "구독 중인 플레이리스트에 콘텐츠가 추가되었습니다";
  private static final String PLAYLIST_CONTENT_ADDED_CONTENT_FORMAT =
      "'%s' 플레이리스트에 '%s' 콘텐츠가 추가되었습니다.";
  private static final String FOLLOWEE_ACTIVITY_TITLE = "팔로우한 사용자의 새 활동";
  private static final String FOLLOWEE_ACTIVITY_CONTENT_FORMAT = "%s님이 새 플레이리스트를 만들었습니다.";
  private static final String USER_ROLE_CHANGED_TITLE = "권한이 변경되었습니다";
  private static final String USER_ROLE_CHANGED_CONTENT_FORMAT = "회원님의 권한이 %s로 변경되었습니다.";
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
  public void handlePlaylistContentAdded(PlaylistContentAddedEvent event) {
    event.subscriberIds().forEach(subscriberId ->
        notificationService.create(new NotificationCreateCommand(
            subscriberId,
            PLAYLIST_CONTENT_ADDED_TITLE,
            PLAYLIST_CONTENT_ADDED_CONTENT_FORMAT.formatted(
                event.playlistTitle(),
                event.contentTitle()
            ),
            NotificationLevel.INFO
        ))
    );
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePlaylistCreated(PlaylistCreatedEvent event) {
    event.followerIds().forEach(followerId ->
        notificationService.create(new NotificationCreateCommand(
            followerId,
            FOLLOWEE_ACTIVITY_TITLE,
            FOLLOWEE_ACTIVITY_CONTENT_FORMAT.formatted(displayName(event.ownerName())),
            NotificationLevel.INFO
        ))
    );
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserRoleChanged(UserRoleChangedEvent event) {
    notificationService.create(new NotificationCreateCommand(
        event.userId(),
        USER_ROLE_CHANGED_TITLE,
        USER_ROLE_CHANGED_CONTENT_FORMAT.formatted(event.role().name()),
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
