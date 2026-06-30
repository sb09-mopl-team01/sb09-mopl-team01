package io.mopl.domain.directmessage.repository;

import static io.mopl.domain.directmessage.entity.QConversation.conversation;
import static io.mopl.domain.user.entity.QUser.user;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class ConversationRepositoryImpl implements ConversationRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Conversation> findMyConversationsWithCursor(
      UUID requesterId,
      String keywordLike,
      Instant cursor,
      UUID idAfter,
      SortDirection sortDirection,
      Pageable pageable
  ) {
    return queryFactory
        .selectFrom(conversation)
        .where(
            participantCondition(requesterId),
            keywordCondition(requesterId, keywordLike),
            cursorCondition(cursor, idAfter, sortDirection)
        )
        .orderBy(createdAtOrder(sortDirection), idOrder(sortDirection))
        .limit(pageable.getPageSize())
        .fetch();
  }

  @Override
  public long countMyConversations(UUID requesterId, String keywordLike) {
    Long count = queryFactory
        .select(conversation.count())
        .from(conversation)
        .where(
            participantCondition(requesterId),
            keywordCondition(requesterId, keywordLike)
        )
        .fetchOne();

    return count == null ? 0 : count;
  }

  private BooleanExpression participantCondition(UUID requesterId) {
    return conversation.participantAId.eq(requesterId)
        .or(conversation.participantBId.eq(requesterId));
  }

  private BooleanExpression keywordCondition(UUID requesterId, String keywordLike) {
    if (!StringUtils.hasText(keywordLike)) {
      return null;
    }

    return conversation.participantAId.eq(requesterId)
        .and(JPAExpressions
            .selectOne()
            .from(user)
            .where(
                user.id.eq(conversation.participantBId),
                user.name.containsIgnoreCase(keywordLike)
            )
            .exists())
        .or(conversation.participantBId.eq(requesterId)
            .and(JPAExpressions
                .selectOne()
                .from(user)
                .where(
                    user.id.eq(conversation.participantAId),
                    user.name.containsIgnoreCase(keywordLike)
                )
                .exists()));
  }

  private BooleanExpression cursorCondition(Instant cursor, UUID idAfter, SortDirection sortDirection) {
    if (cursor == null) {
      return null;
    }

    if (sortDirection == SortDirection.ASCENDING) {
      BooleanExpression newerThanCursor = conversation.createdAt.gt(cursor);
      if (idAfter == null) {
        return newerThanCursor;
      }
      return newerThanCursor.or(
          conversation.createdAt.eq(cursor)
              .and(conversation.id.gt(idAfter))
      );
    }

    BooleanExpression olderThanCursor = conversation.createdAt.lt(cursor);
    if (idAfter == null) {
      return olderThanCursor;
    }
    return olderThanCursor.or(
        conversation.createdAt.eq(cursor)
            .and(conversation.id.lt(idAfter))
    );
  }

  private OrderSpecifier<Instant> createdAtOrder(SortDirection sortDirection) {
    if (sortDirection == SortDirection.ASCENDING) {
      return conversation.createdAt.asc();
    }
    return conversation.createdAt.desc();
  }

  private OrderSpecifier<UUID> idOrder(SortDirection sortDirection) {
    if (sortDirection == SortDirection.ASCENDING) {
      return conversation.id.asc();
    }
    return conversation.id.desc();
  }
}
