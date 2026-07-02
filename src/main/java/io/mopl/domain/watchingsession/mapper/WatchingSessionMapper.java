package io.mopl.domain.watchingsession.mapper;

import io.mopl.domain.content.dto.ContentStats;
import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.content.mapper.ContentMapper;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.watchingsession.dto.WatchingSessionDto;
import io.mopl.domain.watchingsession.entity.WatchingSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WatchingSessionMapper {

  private final ContentMapper contentMapper;

  public WatchingSessionDto toDto(WatchingSession session) {
    return new WatchingSessionDto(
        session.getId(),
        session.getCreatedAt(),
        toUserSummary(session.getWatcher()),
        toContentSummary(session)
    );
  }

  private UserSummary toUserSummary(User watcher) {
    return new UserSummary(
        watcher.getId(),
        watcher.getName(),
        watcher.getProfileImageUrl()
    );
  }

  private ContentSummary toContentSummary(WatchingSession session) {
    return contentMapper.toSummary(
        session.getContent(),
        new ContentStats(
            session.getContent().getAverageRating(),
            session.getContent().getReviewCount(),
            0L
        )
    );
  }
}
