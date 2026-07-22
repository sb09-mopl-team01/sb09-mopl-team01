package io.mopl.domain.directmessage.repository;

import static io.mopl.domain.directmessage.entity.QDirectMessage.directMessage;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.directmessage.entity.DirectMessage;
import io.mopl.domain.directmessage.entity.QDirectMessage;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DirectMessageRepositoryImpl implements DirectMessageRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<DirectMessage> findByConversationIdWithCursor(
      UUID conversationId,
      Instant cursor,
      UUID idAfter,
      SortDirection sortDirection,
      Pageable pageable
  ) {
    return queryFactory
        .selectFrom(directMessage)
        .where(
            directMessage.conversation.id.eq(conversationId),
            cursorCondition(cursor, idAfter, sortDirection)
        )
        .orderBy(createdAtOrder(sortDirection), idOrder(sortDirection))
        .limit(pageable.getPageSize())
        .fetch();
  }

  @Override
  public long countByConversationId(UUID conversationId) {
    Long count = queryFactory
        .select(directMessage.count())
        .from(directMessage)
        .where(directMessage.conversation.id.eq(conversationId))
        .fetchOne();

    return count == null ? 0 : count;
  }

  @Override
  public List<DirectMessage> findLastestByConversationIds(List<UUID> conversationIds) {
    if (conversationIds == null || conversationIds.isEmpty()) {
      return List.of();
    }

    QDirectMessage newerDirectMessage = new QDirectMessage("newerDirectMessage");

    return queryFactory
        .selectFrom(directMessage)
        .where(
            directMessage.conversation.id.in(conversationIds),
            JPAExpressions
                .selectOne()
                .from(newerDirectMessage)
                .where(
                    newerDirectMessage.conversation.id.eq(directMessage.conversation.id),
                    newerDirectMessage.createdAt.gt(directMessage.createdAt)
                        .or(newerDirectMessage.createdAt.eq(directMessage.createdAt)
                            .and(newerDirectMessage.id.gt(directMessage.id)))
                )
                .notExists()
        )
        .orderBy(
            directMessage.conversation.id.asc(),
            directMessage.createdAt.desc(),
            directMessage.id.desc()
        )
        .fetch();
  }

  private BooleanExpression cursorCondition(Instant cursor, UUID idAfter, SortDirection sortDirection) {
    if (cursor == null) {
      return null;
    }

    if (sortDirection == SortDirection.ASCENDING) {
      BooleanExpression newerThanCursor = directMessage.createdAt.gt(cursor);
      if (idAfter == null) {
        return newerThanCursor;
      }
      return newerThanCursor.or(
          directMessage.createdAt.eq(cursor)
              .and(directMessage.id.gt(idAfter))
      );
    }

    BooleanExpression olderThanCursor = directMessage.createdAt.lt(cursor);
    if (idAfter == null) {
      return olderThanCursor;
    }
    return olderThanCursor.or(
        directMessage.createdAt.eq(cursor)
            .and(directMessage.id.lt(idAfter))
    );
  }

  private OrderSpecifier<Instant> createdAtOrder(SortDirection sortDirection) {
    if (sortDirection == SortDirection.ASCENDING) {
      return directMessage.createdAt.asc();
    }
    return directMessage.createdAt.desc();
  }

  private OrderSpecifier<UUID> idOrder(SortDirection sortDirection) {
    if (sortDirection == SortDirection.ASCENDING) {
      return directMessage.id.asc();
    }
    return directMessage.id.desc();
  }
}
