package io.mopl.domain.content.repository;

import static io.mopl.domain.content.entity.QContent.content;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentRepositoryImpl implements ContentRepositoryCustom {

  private static final String SORT_BY_CREATED_AT = "createdAt";
  private static final String SORT_BY_RATE = "rate";
  private static final String SORT_BY_WATCHER_COUNT = "watcherCount";

  private final JPAQueryFactory queryFactory;

  @Override
  public CursorResponse<Content> findContentsByCursor(
      ContentType typeEqual,
      String keywordLike,
      Collection<String> tagsIn,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      SortDirection sortDirection
  ) {
    String resolvedSortBy = resolveSortBy(sortBy);
    SortDirection resolvedDirection = sortDirection == null ? SortDirection.DESCENDING : sortDirection;

    List<Content> contents = queryFactory
        .selectFrom(content)
        .distinct()
        .where(
            eqType(typeEqual),
            containsKeyword(keywordLike),
            containsAnyTag(tagsIn),
            cursorCondition(cursor, idAfter, resolvedSortBy, resolvedDirection)
        )
        .orderBy(
            createOrderSpecifier(resolvedSortBy, resolvedDirection),
            createIdOrderSpecifier(resolvedDirection)
        )
        .limit(limit + 1)
        .fetch();

    boolean hasNext = contents.size() > limit;
    if (hasNext) {
      contents.remove(limit);
    }

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (!contents.isEmpty()) {
      Content lastContent = contents.get(contents.size() - 1);
      nextCursor = nextCursor(lastContent, resolvedSortBy);
      nextIdAfter = lastContent.getId();
    }

    Long totalCount = queryFactory
        .select(content.id.countDistinct())
        .from(content)
        .where(
            eqType(typeEqual),
            containsKeyword(keywordLike),
            containsAnyTag(tagsIn)
        )
        .fetchOne();

    return new CursorResponse<>(
        contents,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount == null ? 0L : totalCount,
        resolvedSortBy,
        resolvedDirection
    );
  }

  private String resolveSortBy(String sortBy) {
    if (sortBy == null || sortBy.isBlank()) {
      return SORT_BY_CREATED_AT;
    }
    if (SORT_BY_CREATED_AT.equals(sortBy) || SORT_BY_RATE.equals(sortBy)
        || SORT_BY_WATCHER_COUNT.equals(sortBy)) {
      return sortBy;
    }
    throw new IllegalArgumentException("Unsupported content sortBy: " + sortBy);
  }

  private BooleanExpression eqType(ContentType typeEqual) {
    return typeEqual == null ? null : content.type.eq(typeEqual);
  }

  private BooleanExpression containsKeyword(String keywordLike) {
    if (keywordLike == null || keywordLike.isBlank()) {
      return null;
    }

    String keyword = keywordLike.trim();
    return content.title.containsIgnoreCase(keyword)
        .or(content.description.containsIgnoreCase(keyword));
  }

  private BooleanExpression containsAnyTag(Collection<String> tagsIn) {
    if (tagsIn == null || tagsIn.isEmpty()) {
      return null;
    }
    return content.tags.any().in(tagsIn);
  }

  private BooleanExpression cursorCondition(
      String cursor,
      UUID idAfter,
      String sortBy,
      SortDirection sortDirection
  ) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }

    if (SORT_BY_RATE.equals(sortBy)) {
      return rateCursorCondition(cursor, idAfter, sortDirection);
    }
    if (SORT_BY_WATCHER_COUNT.equals(sortBy)) {
      return watcherCountCursorCondition(idAfter, sortDirection);
    }
    return createdAtCursorCondition(cursor, idAfter, sortDirection);
  }

  private BooleanExpression createdAtCursorCondition(
      String cursor,
      UUID idAfter,
      SortDirection sortDirection
  ) {
    Instant cursorCreatedAt = Instant.parse(cursor);
    if (sortDirection == SortDirection.ASCENDING) {
      BooleanExpression afterCursor = content.createdAt.gt(cursorCreatedAt);
      if (idAfter == null) {
        return afterCursor;
      }
      return afterCursor.or(content.createdAt.eq(cursorCreatedAt).and(content.id.gt(idAfter)));
    }

    BooleanExpression beforeCursor = content.createdAt.lt(cursorCreatedAt);
    if (idAfter == null) {
      return beforeCursor;
    }
    return beforeCursor.or(content.createdAt.eq(cursorCreatedAt).and(content.id.lt(idAfter)));
  }

  private BooleanExpression rateCursorCondition(
      String cursor,
      UUID idAfter,
      SortDirection sortDirection
  ) {
    double cursorRate = Double.parseDouble(cursor);
    if (sortDirection == SortDirection.ASCENDING) {
      BooleanExpression afterCursor = content.averageRating.gt(cursorRate);
      if (idAfter == null) {
        return afterCursor;
      }
      return afterCursor.or(content.averageRating.eq(cursorRate).and(content.id.gt(idAfter)));
    }

    BooleanExpression beforeCursor = content.averageRating.lt(cursorRate);
    if (idAfter == null) {
      return beforeCursor;
    }
    return beforeCursor.or(content.averageRating.eq(cursorRate).and(content.id.lt(idAfter)));
  }

  private BooleanExpression watcherCountCursorCondition(
      UUID idAfter,
      SortDirection sortDirection
  ) {
    if (idAfter == null) {
      return null;
    }
    return sortDirection == SortDirection.ASCENDING
        ? content.id.gt(idAfter)
        : content.id.lt(idAfter);
  }

  private OrderSpecifier<?> createOrderSpecifier(String sortBy, SortDirection sortDirection) {
    if (SORT_BY_RATE.equals(sortBy)) {
      return sortDirection == SortDirection.ASCENDING
          ? content.averageRating.asc()
          : content.averageRating.desc();
    }
    if (SORT_BY_WATCHER_COUNT.equals(sortBy)) {
      return sortDirection == SortDirection.ASCENDING
          ? content.id.asc()
          : content.id.desc();
    }
    return sortDirection == SortDirection.ASCENDING
        ? content.createdAt.asc()
        : content.createdAt.desc();
  }

  private OrderSpecifier<UUID> createIdOrderSpecifier(SortDirection sortDirection) {
    return sortDirection == SortDirection.ASCENDING
        ? content.id.asc()
        : content.id.desc();
  }

  private String nextCursor(Content content, String sortBy) {
    if (SORT_BY_RATE.equals(sortBy)) {
      return String.valueOf(content.getAverageRating());
    }
    if (SORT_BY_WATCHER_COUNT.equals(sortBy)) {
      return "0";
    }
    return content.getCreatedAt().toString();
  }
}
