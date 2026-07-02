package io.mopl.domain.playlist.service;

import io.mopl.domain.playlist.dto.PlaylistDto;
import io.mopl.domain.playlist.dto.request.PlaylistCreateRequest;
import io.mopl.domain.playlist.dto.request.PlaylistUpdateRequest;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.PlaylistContent;
import io.mopl.domain.playlist.entity.PlaylistSubscription;
import io.mopl.domain.playlist.mapper.PlaylistMapper;
import io.mopl.domain.playlist.repository.PlaylistContentRepository;
import io.mopl.domain.playlist.repository.PlaylistRepository;
import io.mopl.domain.playlist.repository.PlaylistSubscriptionRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistService {

  private final PlaylistRepository playlistRepository;
  private final PlaylistContentRepository playlistContentRepository;
  private final PlaylistSubscriptionRepository playlistSubscriptionRepository;
  private final UserRepository userRepository;
  private final ContentRepository contentRepository;
  private final PlaylistMapper playlistMapper;

  // 임시 / 공동 발행 로직 추가시 변경 후 활성화
  //private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void createPlaylist(UUID userId, PlaylistCreateRequest request) {
    log.info("Attempting to create playlist: userId={}, title={}", userId, request.title());
    User owner = userRepository.findById(userId)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    Playlist playlist = Playlist.create(
        owner,
        request.title(),
        request.description()
    );

    playlistRepository.save(playlist);

    log.info("Playlist created successfully: {}, playlistId: {}, title: {}", userId, playlist.getId(), playlist.getTitle());
  }

  @Transactional
  public void deletePlaylist(UUID userId, UUID playlistId) {
    log.info("Attempting to delete playlist: playlistId={}, userId={}", playlistId, userId);
    Playlist playlist = validateAndGetPlaylistOwnership(userId, playlistId);

    playlistContentRepository.deleteAllByPlaylistId(playlistId);
    playlistSubscriptionRepository.deleteAllByPlaylistId(playlistId);
    playlistRepository.delete(playlist);

    log.info("Playlist deleted successfully: playlistId={}, ownerId={}", playlistId, userId);
  }

  @Transactional
  public void subscribePlaylist(UUID userId, UUID playlistId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new BaseException(ErrorCode.PLAYLIST_NOT_FOUND));
    User subscriber = userRepository.findById(userId)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    if (playlist.getOwner().getId().equals(userId)) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    if (playlistSubscriptionRepository.existsByPlaylistAndUser(playlist, subscriber)) {
      throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
    }

    playlistSubscriptionRepository.save(new PlaylistSubscription(playlist, subscriber));
    playlist.increaseSubscriberCount();

    log.info("Playlist subscribed successfully: playlistId={}, subscriberId={}", playlistId, userId);

//    로직 추가시 반영
//    내 플리 구독 발생 / 이벤트 발행
//    eventPublisher.publishEvent(new PlaylistSubscribedEvent(
//        playlist.getOwner().getId(),
//        subscriber.getName(),
//        playlist.getTitle()
//    ));
  }

  @Transactional
  public void unsubscribePlaylist(UUID userId, UUID playlistId) {
    log.info("Attempting to unsubscribe from playlist: playlistId={}, userId={}", playlistId, userId);

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new BaseException(ErrorCode.PLAYLIST_NOT_FOUND));
    User subscriber = userRepository.findById(userId)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

    // 구독 정보가 없을 때 삭제 시도 시 -> 공통 400
    PlaylistSubscription subscription = playlistSubscriptionRepository
        .findByPlaylistAndUser(playlist, subscriber)
        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));

    playlistSubscriptionRepository.delete(subscription);
    playlist.decreaseSubscriberCount();

    log.info("Playlist unsubscribed successfully: playlistId={}, subscriberId={}", playlistId, userId);  }

  @Transactional
  public void addContentToPlaylist(UUID userId, UUID playlistId, UUID contentId) {
    log.info("Attempting to add content to playlist: playlistId={}, contentId={}, userId={}", playlistId, contentId, userId);
    Playlist playlist = validateAndGetPlaylistOwnership(userId, playlistId);

    // 콘텐츠가 없을때 에러코드 -> 공통 400 적용 상의 후 추가
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));

    if (playlistContentRepository.existsByPlaylistAndContent(playlist, content)) {
      throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
    }

    playlistContentRepository.save(new PlaylistContent(playlist, content));

    log.info("Content added to playlist successfully: playlistId={}, contentId={}", playlistId, contentId);
//    로직 추가시 반영
//    구독 중인 플리에 신규 콘텐츠 추가 / 이벤트 발행
//    eventPublisher.publishEvent(new PlaylistContentAddedEvent(
//        playlistId,
//        playlist.getTitle(),
//        content.getTitle()
//    ));
  }

  @Transactional
  public void removeContentFromPlaylist(UUID userId, UUID playlistId, UUID contentId) {
    log.info("Attempting to remove content from playlist: playlistId={}, contentId={}, userId={}", playlistId, contentId, userId);
    Playlist playlist = validateAndGetPlaylistOwnership(userId, playlistId);

    // 콘텐츠가 없을때 에러코드 -> 공통 400 적용 상의 후 추가
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));

    PlaylistContent playlistContent = playlistContentRepository
        .findByPlaylistAndContent(playlist, content)
        .orElseThrow(() -> new BaseException(ErrorCode.PLAYLIST_CONTENT_NOT_FOUND));

    playlistContentRepository.delete(playlistContent);

    log.info("Content removed from playlist successfully: playlistId={}, contentId={}", playlistId, contentId);  }

  @Transactional
  public void updatePlaylist(UUID userId, UUID playlistId, PlaylistUpdateRequest request) {
    log.info("Attempting to update playlist: playlistId={}, userId={}", playlistId, userId);
    Playlist playlist = validateAndGetPlaylistOwnership(userId, playlistId);

    playlist.update(
        request.title(),
        request.description()
    );
    log.info("Playlist updated successfully: playlistId={}, ownerId={}", playlistId, userId);
  }

  private Playlist validateAndGetPlaylistOwnership(UUID userId, UUID playlistId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new BaseException(ErrorCode.PLAYLIST_NOT_FOUND));

    if (!playlist.getOwner().getId().equals(userId)) {
      log.warn("Unauthorized attempt to modify playlist: playlistId={}, requesterId={}", playlistId, userId);
      throw new BaseException(ErrorCode.FORBIDDEN);
    }
    return playlist;
  }

  public PlaylistDto findPlaylist(UUID userId, UUID playlistId) {
    log.info("Attempting to find playlist: playlistId={}, requesterId={}", playlistId, userId);

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new BaseException(ErrorCode.PLAYLIST_NOT_FOUND));

    boolean subscribedByMe = isSubscribedBy(playlist, userId);
    return playlistMapper.toDto(playlist, subscribedByMe);
  }

  public CursorResponse<PlaylistDto> findPlaylists(
      UUID userId, String keyword, UUID ownerId, UUID subscriberId,
      String cursor, UUID idAfter, int limit, String sortDirection, String sortBy) {

    log.info("Attempting to find playlists by cursor: requesterId={}, limit={}, sortBy={}", userId, limit, sortBy);

    List<Playlist> playlists = playlistRepository.findPlaylistsByCursor(
        keyword, ownerId, subscriberId, cursor, idAfter, limit, sortDirection, sortBy
    );

    boolean hasNext = playlists.size() > limit;
    if (hasNext) {
      playlists = playlists.subList(0, limit);
    }

    List<PlaylistDto> playlistDtos = playlists.stream()
        .map(playlist -> playlistMapper.toDto(playlist, isSubscribedBy(playlist, userId)))
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext && !playlists.isEmpty()) {
      Playlist lastPlaylist = playlists.get(playlists.size() - 1);
      nextIdAfter = lastPlaylist.getId();
      nextCursor = "subscribeCount".equals(sortBy)
          ? String.valueOf(lastPlaylist.getSubscriberCount())
          : (lastPlaylist.getUpdatedAt() != null ? lastPlaylist.getUpdatedAt().toString() : lastPlaylist.getCreatedAt().toString());
    }

    long totalCount = playlistRepository.countPlaylists(keyword, ownerId, subscriberId);

    return new CursorResponse<>(
        playlistDtos,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy,
        SortDirection.valueOf(sortDirection.toUpperCase())
    );
  }

  private boolean isSubscribedBy(Playlist playlist, UUID userId) {
    if (userId == null) return false;
    User requester = userRepository.findById(userId).orElse(null);
    if (requester == null) return false;

    return playlistSubscriptionRepository.existsByPlaylistAndUser(playlist, requester);
  }
}