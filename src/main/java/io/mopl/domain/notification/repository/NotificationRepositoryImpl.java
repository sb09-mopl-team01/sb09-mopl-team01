package io.mopl.domain.notification.repository;

import static io.mopl.domain.notification.entity.QNotification.notification;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Notification> findByReceiverIdWithCursorDesc(
      UUID receiverId,
      Instant cursor,
      UUID idAfter,
      Pageable pageable
  ) {
    return queryFactory
        .selectFrom(notification)
        .where(
            notification.receiverId.eq(receiverId),
            cursorConditionDesc(cursor, idAfter)
        )
        .orderBy(notification.createdAt.desc(), notification.id.desc())
        .limit(pageable.getPageSize())
        .fetch();
  }

  @Override
  public List<Notification> findByReceiverIdWithCursorAsc(
      UUID receiverId,
      Instant cursor,
      UUID idAfter,
      Pageable pageable
  ) {
    return queryFactory
        .selectFrom(notification)
        .where(
            notification.receiverId.eq(receiverId),
            cursorConditionAsc(cursor, idAfter)
        )
        .orderBy(notification.createdAt.asc(), notification.id.asc())
        .limit(pageable.getPageSize())
        .fetch();
  }

  private BooleanExpression cursorConditionDesc(Instant cursor, UUID idAfter) {
    if (cursor == null) {
      return null;
    }

    BooleanExpression olderThanCursor = notification.createdAt.lt(cursor);
    if (idAfter == null) {
      return olderThanCursor;
    }

    return olderThanCursor.or(
        notification.createdAt.eq(cursor)
            .and(notification.id.lt(idAfter))
    );
  }

  private BooleanExpression cursorConditionAsc(Instant cursor, UUID idAfter) {
    if (cursor == null) {
      return null;
    }

    BooleanExpression newerThanCursor = notification.createdAt.gt(cursor);
    if (idAfter == null) {
      return newerThanCursor;
    }

    return newerThanCursor.or(
        notification.createdAt.eq(cursor)
            .and(notification.id.gt(idAfter))
    );
  }
}
