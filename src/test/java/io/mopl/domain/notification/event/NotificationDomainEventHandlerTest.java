package io.mopl.domain.notification.event;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import io.mopl.domain.directmessage.event.DirectMessageSentEvent;
import io.mopl.domain.follow.event.FollowCreatedEvent;
import io.mopl.domain.notification.dto.NotificationCreateCommand;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.service.NotificationService;
import io.mopl.domain.playlist.event.PlaylistContentAddedEvent;
import io.mopl.domain.playlist.event.PlaylistCreatedEvent;
import io.mopl.domain.playlist.event.PlaylistSubscribedEvent;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.event.UserRoleChangedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDomainEventHandlerTest {

  private final NotificationService notificationService =
      org.mockito.Mockito.mock(NotificationService.class);
  private final NotificationDomainEventHandler eventHandler =
      new NotificationDomainEventHandler(notificationService);

  @Test
  @DisplayName("팔로우 생성 이벤트를 팔로우 대상자의 알림으로 변환한다")
  void handleFollowCreated() {
    UUID followeeId = UUID.randomUUID();
    FollowCreatedEvent event = new FollowCreatedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "follower",
        followeeId,
        Instant.parse("2026-07-07T01:00:00Z")
    );

    eventHandler.handleFollowCreated(event);

    verify(notificationService).create(argThat(command ->
        command.receiverId().equals(followeeId)
            && command.title().equals("새 팔로워가 생겼습니다")
            && command.content().equals("follower님이 회원님을 팔로우했습니다.")
            && command.level() == NotificationLevel.INFO
    ));
  }

  @Test
  @DisplayName("이벤트 발신자 이름이 비어 있으면 기본 표시 이름으로 알림을 생성한다")
  void handleFollowCreatedWithBlankName() {
    UUID followeeId = UUID.randomUUID();
    FollowCreatedEvent event = new FollowCreatedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "",
        followeeId,
        Instant.parse("2026-07-07T01:00:00Z")
    );

    eventHandler.handleFollowCreated(event);

    verify(notificationService).create(argThat(command ->
        command.receiverId().equals(followeeId)
            && command.content().equals("사용자님이 회원님을 팔로우했습니다.")
    ));
  }

  @Test
  @DisplayName("플레이리스트 구독 이벤트를 플레이리스트 소유자의 알림으로 변환한다")
  void handlePlaylistSubscribed() {
    UUID ownerId = UUID.randomUUID();
    PlaylistSubscribedEvent event = new PlaylistSubscribedEvent(
        UUID.randomUUID(),
        "주말 영화",
        ownerId,
        UUID.randomUUID(),
        "subscriber",
        Instant.parse("2026-07-07T01:00:00Z")
    );

    eventHandler.handlePlaylistSubscribed(event);

    verify(notificationService).create(argThat(command ->
        command.receiverId().equals(ownerId)
            && command.title().equals("플레이리스트를 구독했습니다")
            && command.content().equals("subscriber님이 '주말 영화' 플레이리스트를 구독했습니다.")
            && command.level() == NotificationLevel.INFO
    ));
  }

  @Test
  @DisplayName("플레이리스트 콘텐츠 추가 이벤트를 구독자 알림으로 변환한다")
  void handlePlaylistContentAdded() {
    UUID subscriberId = UUID.randomUUID();
    PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(
        UUID.randomUUID(),
        "주말 영화",
        UUID.randomUUID(),
        "새 영화",
        List.of(subscriberId),
        Instant.parse("2026-07-07T01:00:00Z")
    );

    eventHandler.handlePlaylistContentAdded(event);

    verify(notificationService).create(argThat(command ->
        command.receiverId().equals(subscriberId)
            && command.title().equals("구독 중인 플레이리스트에 콘텐츠가 추가되었습니다")
            && command.content().equals("'주말 영화' 플레이리스트에 '새 영화' 콘텐츠가 추가되었습니다.")
            && command.level() == NotificationLevel.INFO
    ));
  }

  @Test
  @DisplayName("플레이리스트 생성 이벤트를 팔로워 주요 활동 알림으로 변환한다")
  void handlePlaylistCreated() {
    UUID followerId = UUID.randomUUID();
    PlaylistCreatedEvent event = new PlaylistCreatedEvent(
        UUID.randomUUID(),
        "새 플레이리스트",
        UUID.randomUUID(),
        "creator",
        List.of(followerId),
        Instant.parse("2026-07-07T01:00:00Z")
    );

    eventHandler.handlePlaylistCreated(event);

    verify(notificationService).create(argThat(command ->
        command.receiverId().equals(followerId)
            && command.title().equals("팔로우한 사용자의 새 활동")
            && command.content().equals("creator님이 새 플레이리스트를 만들었습니다.")
            && command.level() == NotificationLevel.INFO
    ));
  }

  @Test
  @DisplayName("권한 변경 이벤트를 대상 사용자 알림으로 변환한다")
  void handleUserRoleChanged() {
    UUID userId = UUID.randomUUID();
    UserRoleChangedEvent event = new UserRoleChangedEvent(
        userId,
        Role.ADMIN,
        Instant.parse("2026-07-07T01:00:00Z")
    );

    eventHandler.handleUserRoleChanged(event);

    verify(notificationService).create(argThat(command ->
        command.receiverId().equals(userId)
            && command.title().equals("권한이 변경되었습니다")
            && command.content().equals("회원님의 권한이 ADMIN로 변경되었습니다.")
            && command.level() == NotificationLevel.INFO
    ));
  }

  @Test
  @DisplayName("DM 전송 이벤트를 수신자의 알림으로 변환하고 본문은 노출하지 않는다")
  void handleDirectMessageSent() {
    UUID receiverId = UUID.randomUUID();
    DirectMessageSentEvent event = new DirectMessageSentEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "sender",
        receiverId,
        Instant.parse("2026-07-07T01:00:00Z")
    );

    eventHandler.handleDirectMessageSent(event);

    verify(notificationService).create(argThat(command ->
        command.receiverId().equals(receiverId)
            && command.title().equals("새 DM이 도착했습니다")
            && command.content().equals("sender님이 메시지를 보냈습니다.")
            && command.level() == NotificationLevel.INFO
    ));
  }
}
