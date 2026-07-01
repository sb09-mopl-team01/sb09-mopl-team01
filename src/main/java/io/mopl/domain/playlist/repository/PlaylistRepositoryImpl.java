package io.mopl.domain.playlist.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.QPlaylist;
import io.mopl.domain.playlist.entity.QPlaylistSubscription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PlaylistRepositoryImpl implements PlaylistRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Playlist> findPlaylistsByCursor(
      String keyword, UUID ownerId, UUID subscriberId,
      String cursor, UUID idAfter, int limit,
      String sortDirection, String sortBy) {

    QPlaylist playlist = QPlaylist.playlist;
    QPlaylistSubscription subscription = QPlaylistSubscription.playlistSubscription;

    return queryFactory.selectFrom(playlist)
        // 구독자 필터가 있을 때만 조인해서 성능 최적화
        .leftJoin(subscription).on(subscription.playlist.eq(playlist))
        .where(
            keywordLike(keyword),
            ownerEq(ownerId),
            subscriberEq(subscriberId),
            cursorCondition(cursor, idAfter, sortDirection, sortBy)
        )
        .groupBy(playlist.id)
        .orderBy(getSortOrder(sortDirection, sortBy), getTieBreakerOrder(sortDirection))
        .limit(limit + 1)
        .fetch();
  }

  @Override
  public long countPlaylists(String keyword, UUID ownerId, UUID subscriberId) {
    QPlaylist playlist = QPlaylist.playlist;
    QPlaylistSubscription subscription = QPlaylistSubscription.playlistSubscription;

    Long count = queryFactory.select(playlist.countDistinct())
        .from(playlist)
        .leftJoin(subscription).on(subscription.playlist.eq(playlist))
        .where(
            keywordLike(keyword),
            ownerEq(ownerId),
            subscriberEq(subscriberId)
        )
        .fetchOne();
    return count != null ? count : 0L;
  }

  private BooleanExpression keywordLike(String keyword) {
    if (keyword == null || keyword.isBlank()) return null;
    return QPlaylist.playlist.title.containsIgnoreCase(keyword)
        .or(QPlaylist.playlist.description.containsIgnoreCase(keyword));
  }

  private BooleanExpression ownerEq(UUID ownerId) {
    if (ownerId == null) return null;
    return QPlaylist.playlist.owner.id.eq(ownerId);
  }

  private BooleanExpression subscriberEq(UUID subscriberId) {
    if (subscriberId == null) return null;
    return QPlaylistSubscription.playlistSubscription.user.id.eq(subscriberId);
  }

  private BooleanExpression cursorCondition(String cursor, UUID idAfter, String sortDirection, String sortBy) {
    if (cursor == null || idAfter == null) return null;

    QPlaylist playlist = QPlaylist.playlist;
    boolean isAsc = "ASCENDING".equalsIgnoreCase(sortDirection);

    if ("subscribeCount".equals(sortBy)) {
      Long cursorValue = Long.parseLong(cursor);
      return isAsc ?
          playlist.subscriberCount.gt(cursorValue).or(playlist.subscriberCount.eq(cursorValue).and(playlist.id.gt(idAfter))) :
          playlist.subscriberCount.lt(cursorValue).or(playlist.subscriberCount.eq(cursorValue).and(playlist.id.lt(idAfter)));
    } else {
      Instant cursorValue = Instant.parse(cursor);
      return isAsc ?
          playlist.updatedAt.gt(cursorValue).or(playlist.updatedAt.eq(cursorValue).and(playlist.id.gt(idAfter))) :
          playlist.updatedAt.lt(cursorValue).or(playlist.updatedAt.eq(cursorValue).and(playlist.id.lt(idAfter)));
    }
  }

  private OrderSpecifier<?> getSortOrder(String sortDirection, String sortBy) {
    boolean isAsc = "ASCENDING".equalsIgnoreCase(sortDirection);
    if ("subscribeCount".equals(sortBy)) {
      return isAsc ? QPlaylist.playlist.subscriberCount.asc() : QPlaylist.playlist.subscriberCount.desc();
    }
    return isAsc ? QPlaylist.playlist.updatedAt.asc() : QPlaylist.playlist.updatedAt.desc();
  }

  private OrderSpecifier<UUID> getTieBreakerOrder(String sortDirection) {
    boolean isAsc = "ASCENDING".equalsIgnoreCase(sortDirection);
    return isAsc ? QPlaylist.playlist.id.asc() : QPlaylist.playlist.id.desc();
  }
}