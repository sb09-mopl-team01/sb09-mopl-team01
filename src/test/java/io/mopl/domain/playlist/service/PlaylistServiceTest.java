package io.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.follow.repository.FollowRepository;
import io.mopl.domain.playlist.dto.request.PlaylistCreateRequest;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.PlaylistContent;
import io.mopl.domain.playlist.entity.PlaylistSubscription;
import io.mopl.domain.playlist.event.PlaylistContentAddedEvent;
import io.mopl.domain.playlist.event.PlaylistCreatedEvent;
import io.mopl.domain.playlist.event.PlaylistSubscribedEvent;
import io.mopl.domain.playlist.mapper.PlaylistMapper;
import io.mopl.domain.playlist.repository.PlaylistRepository;
import io.mopl.domain.playlist.repository.PlaylistContentRepository;
import io.mopl.domain.playlist.repository.PlaylistSubscriptionRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

  @Mock private PlaylistRepository playlistRepository;
  @Mock private PlaylistContentRepository playlistContentRepository;
  @Mock private PlaylistSubscriptionRepository playlistSubscriptionRepository;
  @Mock private UserRepository userRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private FollowRepository followRepository;
  @Mock private PlaylistMapper playlistMapper;
  @Mock private DomainEventPublisher eventPublisher;

  @InjectMocks
  private PlaylistService playlistService;

  @Test
  @DisplayName("플레이리스트 생성 성공")
  void createPlaylist_Success() {

    UUID userId = UUID.randomUUID();
    User owner = User.builder().name("Test User").build();
    ReflectionTestUtils.setField(owner, "id", userId);

    PlaylistCreateRequest request = new PlaylistCreateRequest("Test Title", "Test Description");
    Playlist savedPlaylist = Playlist.create(owner, "Test Title", "Test Description");
    ReflectionTestUtils.setField(savedPlaylist, "id", UUID.randomUUID());

    given(userRepository.findById(userId)).willReturn(Optional.of(owner));
    given(playlistRepository.save(any(Playlist.class))).willReturn(savedPlaylist);
    given(followRepository.findFollowerIdsByFolloweeId(userId)).willReturn(List.of());

    playlistService.createPlaylist(userId, request);

    verify(playlistRepository).save(any(Playlist.class));
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("플레이리스트 생성 성공 시 팔로워 활동 알림 이벤트를 발행한다")
  void createPlaylist_Success_PublishesFollowerActivityEvent() {
    UUID userId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", userId);
    Playlist savedPlaylist = Playlist.create(owner, "New Playlist", "Description");
    ReflectionTestUtils.setField(savedPlaylist, "id", playlistId);

    PlaylistCreateRequest request = new PlaylistCreateRequest("New Playlist", "Description");

    given(userRepository.findById(userId)).willReturn(Optional.of(owner));
    given(playlistRepository.save(any(Playlist.class))).willReturn(savedPlaylist);
    given(followRepository.findFollowerIdsByFolloweeId(userId)).willReturn(List.of(followerId));

    playlistService.createPlaylist(userId, request);

    verify(eventPublisher).publish(argThat(event -> {
      if (!(event instanceof PlaylistCreatedEvent playlistCreatedEvent)) {
        return false;
      }
      return playlistCreatedEvent.playlistId().equals(playlistId)
          && playlistCreatedEvent.playlistTitle().equals("New Playlist")
          && playlistCreatedEvent.ownerId().equals(userId)
          && playlistCreatedEvent.ownerName().equals("Owner")
          && playlistCreatedEvent.followerIds().equals(List.of(followerId))
          && playlistCreatedEvent.occurredAt() != null;
    }));
  }

  @Test
  @DisplayName("권한 없는 플레이리스트 삭제 시도 시 FORBIDDEN 예외 발생")
  void deletePlaylist_Forbidden() {

    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", ownerId);

    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));

    assertThatThrownBy(() -> playlistService.deletePlaylist(requesterId, playlistId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("플레이리스트 구독 성공 시 구독 알림 이벤트를 발행한다")
  void subscribePlaylist_Success_PublishesEvent() {
    UUID ownerId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", ownerId);
    User subscriber = User.builder().name("Subscriber").build();
    ReflectionTestUtils.setField(subscriber, "id", subscriberId);

    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(userRepository.findById(subscriberId)).willReturn(Optional.of(subscriber));
    given(playlistSubscriptionRepository.existsByPlaylistAndUser(playlist, subscriber))
        .willReturn(false);

    playlistService.subscribePlaylist(subscriberId, playlistId);

    verify(playlistSubscriptionRepository).save(any(PlaylistSubscription.class));
    verify(playlistRepository).increaseSubscriberCount(playlistId);
    verify(eventPublisher).publish(argThat(event -> {
      if (!(event instanceof PlaylistSubscribedEvent playlistSubscribedEvent)) {
        return false;
      }
      return playlistSubscribedEvent.playlistId().equals(playlistId)
          && playlistSubscribedEvent.playlistTitle().equals("Title")
          && playlistSubscribedEvent.ownerId().equals(ownerId)
          && playlistSubscribedEvent.subscriberId().equals(subscriberId)
          && playlistSubscribedEvent.subscriberName().equals("Subscriber")
          && playlistSubscribedEvent.occurredAt() != null;
    }));
  }

  @Test
  @DisplayName("본인의 플레이리스트 구독 시도 시 INVALID_INPUT 예외 발생")
  void subscribePlaylist_SelfSubscribe_Fail() {
    UUID userId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", userId);

    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(userRepository.findById(userId)).willReturn(Optional.of(owner));

    assertThatThrownBy(() -> playlistService.subscribePlaylist(userId, playlistId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("이미 구독한 플레이리스트는 구독 알림 이벤트를 발행하지 않는다")
  void subscribePlaylist_Duplicate_Fail_DoesNotPublishEvent() {
    UUID ownerId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", ownerId);
    User subscriber = User.builder().name("Subscriber").build();
    ReflectionTestUtils.setField(subscriber, "id", subscriberId);

    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(userRepository.findById(subscriberId)).willReturn(Optional.of(subscriber));
    given(playlistSubscriptionRepository.existsByPlaylistAndUser(playlist, subscriber))
        .willReturn(true);

    assertThatThrownBy(() -> playlistService.subscribePlaylist(subscriberId, playlistId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("플레이리스트 콘텐츠 추가 성공 시 구독자 알림 이벤트를 발행한다")
  void addContentToPlaylist_Success_PublishesEvent() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", ownerId);
    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);
    Content content = Content.createManual(
        io.mopl.domain.content.entity.ContentType.MOVIE,
        "Content Title",
        "Content Desc",
        "https://example.com/thumb.jpg",
        java.util.Set.of("tag")
    );
    ReflectionTestUtils.setField(content, "id", contentId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
    given(playlistContentRepository.existsByPlaylistAndContent(playlist, content))
        .willReturn(false);
    given(playlistSubscriptionRepository.findSubscriberIdsByPlaylistId(playlistId))
        .willReturn(List.of(subscriberId));

    playlistService.addContentToPlaylist(ownerId, playlistId, contentId);

    verify(playlistContentRepository).save(any(PlaylistContent.class));
    verify(eventPublisher).publish(argThat(event -> {
      if (!(event instanceof PlaylistContentAddedEvent contentAddedEvent)) {
        return false;
      }
      return contentAddedEvent.playlistId().equals(playlistId)
          && contentAddedEvent.playlistTitle().equals("Title")
          && contentAddedEvent.contentId().equals(contentId)
          && contentAddedEvent.contentTitle().equals("Content Title")
          && contentAddedEvent.subscriberIds().equals(List.of(subscriberId))
          && contentAddedEvent.occurredAt() != null;
    }));
  }

  @Test
  @DisplayName("플레이리스트 삭제 성공")
  void deletePlaylist_Success() {
    UUID userId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", userId);
    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));

    playlistService.deletePlaylist(userId, playlistId);

    verify(playlistContentRepository).deleteAllByPlaylistId(playlistId);
    verify(playlistSubscriptionRepository).deleteAllByPlaylistId(playlistId);
    verify(playlistRepository).delete(playlist);
  }

  @Test
  @DisplayName("플레이리스트 구독 취소 성공")
  void unsubscribePlaylist_Success() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
    User subscriber = User.builder().name("Subscriber").build();
    ReflectionTestUtils.setField(subscriber, "id", subscriberId);

    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);

    PlaylistSubscription subscription = new PlaylistSubscription(playlist, subscriber);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(userRepository.findById(subscriberId)).willReturn(Optional.of(subscriber));
    given(playlistSubscriptionRepository.findByPlaylistAndUser(playlist, subscriber))
        .willReturn(Optional.of(subscription));

    playlistService.unsubscribePlaylist(subscriberId, playlistId);

    verify(playlistSubscriptionRepository).delete(subscription);
    verify(playlistRepository).decreaseSubscriberCount(playlistId);
  }

  @Test
  @DisplayName("구독하지 않은 플레이리스트 구독 취소 시도 시 INVALID_INPUT 예외 발생")
  void unsubscribePlaylist_NotSubscribed_Fail() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User subscriber = User.builder().name("Subscriber").build();
    ReflectionTestUtils.setField(subscriber, "id", subscriberId);
    Playlist playlist = Playlist.create(User.builder().build(), "Title", "Desc");

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(userRepository.findById(subscriberId)).willReturn(Optional.of(subscriber));
    given(playlistSubscriptionRepository.findByPlaylistAndUser(playlist, subscriber))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.unsubscribePlaylist(subscriberId, playlistId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
  }

  @Test
  @DisplayName("플레이리스트에 이미 존재하는 콘텐츠 추가 시도 시 DUPLICATE_RESOURCE 예외 발생")
  void addContentToPlaylist_Duplicate_Fail() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", ownerId);
    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);
    Content content = Content.createManual(io.mopl.domain.content.entity.ContentType.MOVIE, "Title", "Desc", "url", java.util.Set.of("테스트태그"));

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
    given(playlistContentRepository.existsByPlaylistAndContent(playlist, content)).willReturn(true);

    assertThatThrownBy(() -> playlistService.addContentToPlaylist(ownerId, playlistId, contentId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
  }

  @Test
  @DisplayName("플레이리스트에서 콘텐츠 삭제 성공")
  void removeContentFromPlaylist_Success() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", ownerId);
    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);
    Content content = Content.createManual(io.mopl.domain.content.entity.ContentType.MOVIE, "Title", "Desc", "url", java.util.Set.of("테스트태그"));

    PlaylistContent playlistContent = new PlaylistContent(playlist, content);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
    given(playlistContentRepository.findByPlaylistAndContent(playlist, content))
        .willReturn(Optional.of(playlistContent));

    playlistService.removeContentFromPlaylist(ownerId, playlistId, contentId);

    verify(playlistContentRepository).delete(playlistContent);
  }

  @Test
  @DisplayName("플레이리스트 수정 성공")
  void updatePlaylist_Success() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", ownerId);
    Playlist playlist = Playlist.create(owner, "Old Title", "Old Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);

    io.mopl.domain.playlist.dto.request.PlaylistUpdateRequest request =
        new io.mopl.domain.playlist.dto.request.PlaylistUpdateRequest("New Title", "New Desc");

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));

    playlistService.updatePlaylist(ownerId, playlistId, request);

  }

  @Test
  @DisplayName("플레이리스트 단건 조회 성공")
  void findPlaylist_Success() {
    UUID userId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    Playlist playlist = Playlist.create(User.builder().build(), "Title", "Desc");

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(playlistSubscriptionRepository.existsByPlaylistIdAndUserId(playlistId, userId)).willReturn(true);

    playlistService.findPlaylist(userId, playlistId);

    verify(playlistMapper).toDto(playlist, true);
  }

  @Test
  @DisplayName("플레이리스트 다건 조회")
  void findPlaylists_HasNext_SortBySubscribeCount() {
    UUID userId = UUID.randomUUID();
    int limit = 1;

    Playlist playlist1 = Playlist.create(User.builder().build(), "Title1", "Desc1");
    ReflectionTestUtils.setField(playlist1, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(playlist1, "subscriberCount", 100L);

    Playlist playlist2 = Playlist.create(User.builder().build(), "Title2", "Desc2");

    given(playlistRepository.findPlaylistsByCursor(any(), any(), any(), any(), any(), any(Integer.class), any(), any()))
        .willReturn(new java.util.ArrayList<>(List.of(playlist1, playlist2)));
    given(playlistRepository.countPlaylists(any(), any(), any())).willReturn(2L);

    io.mopl.global.response.CursorResponse<io.mopl.domain.playlist.dto.PlaylistDto> response =
        playlistService.findPlaylists(null, null, null, null, null, null, limit, "DESCENDING", "subscribeCount");

    org.assertj.core.api.Assertions.assertThat(response.hasNext()).isTrue();
    org.assertj.core.api.Assertions.assertThat(response.nextCursor()).isEqualTo("100");
  }

  @Test
  @DisplayName("플레이리스트 다건 조회")
  void findPlaylists_HasNext_SortByUpdatedAt() {
    int limit = 1;
    java.time.Instant now = java.time.Instant.now();

    Playlist playlist1 = Playlist.create(User.builder().build(), "Title1", "Desc1");
    ReflectionTestUtils.setField(playlist1, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(playlist1, "updatedAt", now);

    Playlist playlist2 = Playlist.create(User.builder().build(), "Title2", "Desc2");

    given(playlistRepository.findPlaylistsByCursor(any(), any(), any(), any(), any(), any(Integer.class), any(), any()))
        .willReturn(new java.util.ArrayList<>(List.of(playlist1, playlist2)));
    given(playlistRepository.countPlaylists(any(), any(), any())).willReturn(2L);

    io.mopl.global.response.CursorResponse<io.mopl.domain.playlist.dto.PlaylistDto> response =
        playlistService.findPlaylists(UUID.randomUUID(), null, null, null, null, null, limit, "DESCENDING", "updatedAt");

    org.assertj.core.api.Assertions.assertThat(response.hasNext()).isTrue();
    org.assertj.core.api.Assertions.assertThat(response.nextCursor()).isEqualTo(now.toString());
  }
}
