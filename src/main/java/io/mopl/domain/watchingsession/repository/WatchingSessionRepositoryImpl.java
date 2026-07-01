package io.mopl.domain.watchingsession.repository;

import static io.mopl.domain.user.entity.QUser.user;
import static io.mopl.domain.watchingsession.entity.QWatchingSession.watchingSession;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.watchingsession.entity.WatchingSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class WatchingSessionRepositoryImpl implements WatchingSessionRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<WatchingSession> findByContentIdWithCursorDesc(
      UUID contentId,
      String watcherNameLike,
      Instant cursor,
      UUID idAfter,
      Pageable pageable
  ) {
    return queryFactory
        .selectFrom(watchingSession)
        .join(watchingSession.watcher, user).fetchJoin()
        .join(watchingSession.content).fetchJoin()
        .where(
            watchingSession.content.id.eq(contentId),
            watcherNameContains(watcherNameLike),
            cursorConditionDesc(cursor, idAfter)
        )
        .orderBy(watchingSession.createdAt.desc(), watchingSession.id.desc())
        .limit(pageable.getPageSize())
        .fetch();
  }

  @Override
  public List<WatchingSession> findByContentIdWithCursorAsc(
      UUID contentId,
      String watcherNameLike,
      Instant cursor,
      UUID idAfter,
      Pageable pageable
  ) {
    return queryFactory
        .selectFrom(watchingSession)
        .join(watchingSession.watcher, user).fetchJoin()
        .join(watchingSession.content).fetchJoin()
        .where(
            watchingSession.content.id.eq(contentId),
            watcherNameContains(watcherNameLike),
            cursorConditionAsc(cursor, idAfter)
        )
        .orderBy(watchingSession.createdAt.asc(), watchingSession.id.asc())
        .limit(pageable.getPageSize())
        .fetch();
  }

  @Override
  public long countByContentId(UUID contentId, String watcherNameLike) {
    Long count = queryFactory
        .select(watchingSession.id.count())
        .from(watchingSession)
        .join(watchingSession.watcher, user)
        .where(
            watchingSession.content.id.eq(contentId),
            watcherNameContains(watcherNameLike)
        )
        .fetchOne();
    return count == null ? 0L : count;
  }

  private BooleanExpression watcherNameContains(String watcherNameLike) {
    if (!StringUtils.hasText(watcherNameLike)) {
      return null;
    }
    return user.name.containsIgnoreCase(watcherNameLike.trim());
  }

  private BooleanExpression cursorConditionDesc(Instant cursor, UUID idAfter) {
    if (cursor == null) {
      return null;
    }

    BooleanExpression olderThanCursor = watchingSession.createdAt.lt(cursor);
    if (idAfter == null) {
      return olderThanCursor;
    }

    return olderThanCursor.or(
        watchingSession.createdAt.eq(cursor)
            .and(watchingSession.id.lt(idAfter))
    );
  }

  private BooleanExpression cursorConditionAsc(Instant cursor, UUID idAfter) {
    if (cursor == null) {
      return null;
    }

    BooleanExpression newerThanCursor = watchingSession.createdAt.gt(cursor);
    if (idAfter == null) {
      return newerThanCursor;
    }

    return newerThanCursor.or(
        watchingSession.createdAt.eq(cursor)
            .and(watchingSession.id.gt(idAfter))
    );
  }
}
