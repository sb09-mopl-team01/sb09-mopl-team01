package io.mopl.domain.review.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.review.entity.QReview;
import io.mopl.domain.review.entity.Review;
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
    if (cursor == null || idAfter == null) {
      return null;
    }

    boolean isAsc = "ASCENDING".equalsIgnoreCase(sortDirection);

    if ("rating".equals(sortBy)) {
      double ratingCursor = Double.parseDouble(cursor);
      if (isAsc) {
        return review.rating.gt(ratingCursor)
            .or(review.rating.eq(ratingCursor).and(review.id.gt(idAfter)));
      } else {
        return review.rating.lt(ratingCursor)
            .or(review.rating.eq(ratingCursor).and(review.id.gt(idAfter)));
      }
    } else {
      Instant timeCursor = Instant.parse(cursor);
      if (isAsc) {
        return review.createdAt.gt(timeCursor)
            .or(review.createdAt.eq(timeCursor).and(review.id.gt(idAfter)));
      } else {
        return review.createdAt.lt(timeCursor)
            .or(review.createdAt.eq(timeCursor).and(review.id.gt(idAfter)));
      }
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