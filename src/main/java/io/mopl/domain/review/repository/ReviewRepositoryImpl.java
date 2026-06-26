package io.mopl.domain.review.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.review.entity.QReview;
import io.mopl.domain.review.entity.Review;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.mopl.domain.review.entity.QReview.review;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Review> findReviewsByCursor(
      UUID contentId, String cursor, UUID idAfter, int limit,
      String sortDirection, String sortBy) {

    return queryFactory
        .selectFrom(review)
        .where(
            contentIdEq(contentId),
            cursorCondition(cursor, idAfter, sortBy, sortDirection)
        )
        .orderBy(
            createOrderSpecifier(sortBy, sortDirection),
            review.id.asc()
        )
        .limit(limit)
        .fetch();
  }

  private BooleanExpression contentIdEq(UUID contentId) {
    return contentId != null ? review.content.id.eq(contentId) : null;
  }

  private BooleanExpression cursorCondition(String cursor, UUID idAfter, String sortBy, String sortDirection) {
    if (cursor == null && idAfter == null) {
      return null;
    }
    if (cursor == null || idAfter == null) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    boolean isAsc = "ASCENDING".equalsIgnoreCase(sortDirection);

    if ("rating".equals(sortBy)) {
      double ratingCursor = Double.parseDouble(cursor);
      return isAsc
          ? review.rating.gt(ratingCursor).or(review.rating.eq(ratingCursor).and(review.id.gt(idAfter)))
          : review.rating.lt(ratingCursor).or(review.rating.eq(ratingCursor).and(review.id.gt(idAfter)));
    } else {
      Instant timeCursor = Instant.parse(cursor);
      return isAsc
          ? review.createdAt.gt(timeCursor).or(review.createdAt.eq(timeCursor).and(review.id.gt(idAfter)))
          : review.createdAt.lt(timeCursor).or(review.createdAt.eq(timeCursor).and(review.id.gt(idAfter)));
    }
  }

  private OrderSpecifier<?> createOrderSpecifier(String sortBy, String sortDirection) {
    boolean isAsc = "ASCENDING".equalsIgnoreCase(sortDirection);

    if ("rating".equals(sortBy)) {
      return isAsc ? review.rating.asc() : review.rating.desc();
    }
    return isAsc ? review.createdAt.asc() : review.createdAt.desc();
  }
}