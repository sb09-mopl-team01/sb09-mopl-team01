package io.mopl.domain.playlist.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.QPlaylist;
import io.mopl.domain.playlist.entity.QPlaylistSubscription;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PlaylistRepositoryImpl implements PlaylistRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  public PlaylistRepositoryImpl(EntityManager em) {
    this.queryFactory = new JPAQueryFactory(em);
  }

  @Override
  public List<Playlist> findPlaylistsByCursor(
      String keywordLike, UUID ownerIdEqual, UUID subscriberIdEqual,
      String cursor, UUID idAfter, int limit, String sortBy, String sortDirection) {

    QPlaylist playlist = QPlaylist.playlist;

    return queryFactory
        .selectFrom(playlist)
        .where(
            containsKeyword(keywordLike),
            eqOwnerId(ownerIdEqual),
            eqSubscriberId(subscriberIdEqual),
            cursorCondition(cursor, idAfter, sortBy, sortDirection)
        )
        .orderBy(getOrderSpecifiers(sortBy, sortDirection))
        .limit(limit + 1L)
        .fetch();
  }

  @Override
  public long countPlaylists(String keywordLike, UUID ownerIdEqual, UUID subscriberIdEqual) {
    QPlaylist playlist = QPlaylist.playlist;

    Long count = queryFactory
        .select(playlist.count())
        .from(playlist)
        .where(
            containsKeyword(keywordLike),
            eqOwnerId(ownerIdEqual),
            eqSubscriberId(subscriberIdEqual)
        )
        .fetchOne();

    return count != null ? count : 0L;
  }


  private BooleanExpression containsKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    QPlaylist playlist = QPlaylist.playlist;
    return playlist.title.containsIgnoreCase(keyword)
        .or(playlist.description.containsIgnoreCase(keyword));
  }

  private BooleanExpression eqOwnerId(UUID ownerId) {
    return ownerId != null ? QPlaylist.playlist.owner.id.eq(ownerId) : null;
  }

  private BooleanExpression eqSubscriberId(UUID subscriberId) {
    if (subscriberId == null) {
      return null;
    }
    QPlaylistSubscription sub = QPlaylistSubscription.playlistSubscription;
    return QPlaylist.playlist.id.in(
        JPAExpressions.select(sub.playlist.id)
            .from(sub)
            .where(sub.user.id.eq(subscriberId))
    );
  }

  private BooleanExpression cursorCondition(String cursor, UUID idAfter, String sortBy, String sortDirection) {
    if (cursor == null || idAfter == null) {
      return null;
    }

    QPlaylist playlist = QPlaylist.playlist;
    boolean isDesc = "DESCENDING".equalsIgnoreCase(sortDirection);

    try {
      // 명세서 스펙: sortBy == "subscribeCount" (구독자수 정렬)
      if ("subscribeCount".equalsIgnoreCase(sortBy)) {
        long cursorValue = Long.parseLong(cursor);
        return isDesc
            ? playlist.subscriberCount.lt(cursorValue).or(playlist.subscriberCount.eq(cursorValue).and(playlist.id.gt(idAfter)))
            : playlist.subscriberCount.gt(cursorValue).or(playlist.subscriberCount.eq(cursorValue).and(playlist.id.gt(idAfter)));
      } else {
        // 기본값: updatedAt (수정일순 정렬)
        Instant cursorValue = Instant.parse(cursor);
        return isDesc
            ? playlist.updatedAt.lt(cursorValue).or(playlist.updatedAt.eq(cursorValue).and(playlist.id.gt(idAfter)))
            : playlist.updatedAt.gt(cursorValue).or(playlist.updatedAt.eq(cursorValue).and(playlist.id.gt(idAfter)));
      }
    } catch (Exception e) {
      return null; // 커서 파싱 실패 시 조건 없이 첫 페이지부터 안전 조회
    }
  }

  private OrderSpecifier<?>[] getOrderSpecifiers(String sortBy, String sortDirection) {
    QPlaylist playlist = QPlaylist.playlist;
    boolean isDesc = "DESCENDING".equalsIgnoreCase(sortDirection);

    List<OrderSpecifier<?>> orders = new ArrayList<>();

    if ("subscribeCount".equalsIgnoreCase(sortBy)) {
      orders.add(isDesc ? playlist.subscriberCount.desc() : playlist.subscriberCount.asc());
    } else {
      orders.add(isDesc ? playlist.updatedAt.desc() : playlist.updatedAt.asc());
    }

    orders.add(playlist.id.asc()); //정렬 보장을 위한 보조 조건 고정

    return orders.toArray(new OrderSpecifier[0]);
  }
}