package io.mopl.domain.watchingsession.service;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.watchingsession.dto.WatchingSessionDto;
import io.mopl.domain.watchingsession.entity.WatchingSession;
import io.mopl.domain.watchingsession.mapper.WatchingSessionMapper;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchingSessionService {

  private static final String CREATED_AT_SORT = "createdAt";

  private final WatchingSessionRepository watchingSessionRepository;
  private final UserRepository userRepository;
  private final ContentRepository contentRepository;
  private final WatchingSessionMapper watchingSessionMapper;

  @Transactional
  public WatchingSessionDto startWatching(UUID watcherId, UUID contentId) {
    validateIds(watcherId, contentId);
    if (watchingSessionRepository.existsByWatcherId(watcherId)) {
      log.warn("Watching session already exists. watcherId={}", watcherId);
      throw new BaseException(ErrorCode.WATCHING_SESSION_ALREADY_EXISTS);
    }

    User watcher = userRepository.findById(watcherId)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));

    WatchingSession session = WatchingSession.start(watcher, content);
    return watchingSessionMapper.toDto(watchingSessionRepository.save(session));
  }

  @Transactional
  public void endWatching(UUID watcherId) {
    if (watcherId == null) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    if (!watchingSessionRepository.existsByWatcherId(watcherId)) {
      throw new BaseException(ErrorCode.WATCHING_SESSION_NOT_FOUND);
    }
    watchingSessionRepository.deleteByWatcherId(watcherId);
  }

  @Transactional(readOnly = true)
  public WatchingSessionDto findByWatcher(UUID watcherId) {
    if (watcherId == null) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    return watchingSessionRepository.findByWatcherId(watcherId)
        .map(watchingSessionMapper::toDto)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public CursorResponse<WatchingSessionDto> findByContent(
      UUID contentId,
      String watcherNameLike,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      SortDirection sortDirection
  ) {
    validateListCommand(contentId, limit, sortBy, sortDirection);

    Instant parsedCursor = parseCursor(cursor);
    PageRequest pageRequest = PageRequest.of(0, limit + 1);
    List<WatchingSession> sessions = sortDirection == SortDirection.ASCENDING
        ? watchingSessionRepository.findByContentIdWithCursorAsc(
            contentId, watcherNameLike, parsedCursor, idAfter, pageRequest)
        : watchingSessionRepository.findByContentIdWithCursorDesc(
            contentId, watcherNameLike, parsedCursor, idAfter, pageRequest);

    boolean hasNext = sessions.size() > limit;
    List<WatchingSession> pageData = sessions.stream()
        .limit(limit)
        .toList();
    List<WatchingSessionDto> data = pageData.stream()
        .map(watchingSessionMapper::toDto)
        .toList();
    WatchingSession lastSession = pageData.isEmpty() ? null : pageData.get(pageData.size() - 1);

    return new CursorResponse<>(
        data,
        hasNext && lastSession != null ? lastSession.getCreatedAt().toString() : null,
        hasNext && lastSession != null ? lastSession.getId() : null,
        hasNext,
        watchingSessionRepository.countByContentId(contentId, watcherNameLike),
        sortBy,
        sortDirection
    );
  }

  private void validateIds(UUID watcherId, UUID contentId) {
    if (watcherId == null || contentId == null) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private void validateListCommand(
      UUID contentId,
      int limit,
      String sortBy,
      SortDirection sortDirection
  ) {
    if (contentId == null || limit <= 0) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    if (!CREATED_AT_SORT.equals(sortBy) || sortDirection == null) {
      throw new BaseException(ErrorCode.INVALID_WATCHING_SESSION_SORT);
    }
  }

  private Instant parseCursor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }

    try {
      return Instant.parse(cursor);
    } catch (DateTimeException e) {
      throw new BaseException(ErrorCode.INVALID_WATCHING_SESSION_CURSOR);
    }
  }
}
