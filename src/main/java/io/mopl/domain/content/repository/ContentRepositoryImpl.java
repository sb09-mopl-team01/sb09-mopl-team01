package io.mopl.domain.content.repository;

import static io.mopl.domain.content.entity.QContent.content;
import static io.mopl.domain.watchingsession.entity.QWatchingSession.watchingSession;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentRepositoryImpl implements ContentRepositoryCustom {

  private static final String SORT_BY_CREATED_AT = "createdAt";
  private static final String SORT_BY_RATE = "rate";
  private static final String SORT_BY_WATCHER_COUNT = "watcherCount";
  private static final String POPULARITY_CURSOR_DELIMITER = "\\|";
  private static final int MIN_INDEXABLE_TRIGRAM_LENGTH = 3;

  private final JPAQueryFactory queryFactory;

  @Override
  public CursorResponse<UUID> findContentIdsByCursor(
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

    if (SORT_BY_WATCHER_COUNT.equals(resolvedSortBy)) {
      return findPopularContentIdsByCursor(
          typeEqual,
          keywordLike,
          tagsIn,
          cursor,
          idAfter,
          limit,
          resolvedDirection
      );
    }

    List<Tuple> rows = queryFactory
        .select(content.id, content.createdAt, content.averageRating, content.reviewCount)
        .from(content)
        .where(
            isActive(),
            eqType(typeEqual),
            containsKeyword(keywordLike),
            containsAnyTag(tagsIn),
            cursorCondition(cursor, idAfter, resolvedSortBy, resolvedDirection)
        )
        .orderBy(
            createOrderSpecifiers(resolvedSortBy, resolvedDirection)
        )
        .limit(limit + 1)
        .fetch();

    boolean hasNext = rows.size() > limit;
    if (hasNext) {
      rows.remove(limit);
    }

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (!rows.isEmpty()) {
      Tuple lastRow = rows.get(rows.size() - 1);
      nextCursor = nextCursor(lastRow, resolvedSortBy);
      nextIdAfter = lastRow.get(content.id);
    }

    List<UUID> contentIds = rows.stream()
        .map(row -> row.get(content.id))
        .toList();

    Long totalCount = queryFactory
        .select(content.id.countDistinct())
        .from(content)
        .where(
            isActive(),
            eqType(typeEqual),
            containsKeyword(keywordLike),
            containsAnyTag(tagsIn)
        )
        .fetchOne();

    return new CursorResponse<>(
        contentIds,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount == null ? 0L : totalCount,
        resolvedSortBy,
        resolvedDirection
    );
  }

  private CursorResponse<UUID> findPopularContentIdsByCursor(
      ContentType typeEqual,
      String keywordLike,
      Collection<String> tagsIn,
      String cursor,
      UUID idAfter,
      int limit,
      SortDirection sortDirection
  ) {
    NumberExpression<Long> watcherCount = watchingSession.id.countDistinct();
    List<Tuple> rows = queryFactory
        .select(content.id, watcherCount, content.reviewCount, content.averageRating)
        .from(content)
        .leftJoin(watchingSession).on(watchingSession.content.eq(content))
        .where(
            isActive(),
            eqType(typeEqual),
            containsKeyword(keywordLike),
            containsAnyTag(tagsIn)
        )
        .groupBy(content.id, content.reviewCount, content.averageRating)
        .having(popularityCursorCondition(
            cursor,
            idAfter,
            sortDirection,
            watcherCount
        ))
        .orderBy(createPopularityOrderSpecifiers(sortDirection, watcherCount))
        .limit(limit + 1)
        .fetch();

    boolean hasNext = rows.size() > limit;
    if (hasNext) {
      rows.remove(limit);
    }

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (!rows.isEmpty()) {
      Tuple lastRow = rows.get(rows.size() - 1);
      Long lastWatcherCount = lastRow.get(watcherCount);
      nextCursor = (lastWatcherCount == null ? 0L : lastWatcherCount)
          + "|" + lastRow.get(content.reviewCount)
          + "|" + lastRow.get(content.averageRating);
      nextIdAfter = lastRow.get(content.id);
    }

    List<UUID> contentIds = rows.stream()
        .map(row -> row.get(content.id))
        .toList();
    Long totalCount = queryFactory
        .select(content.id.countDistinct())
        .from(content)
        .where(
            isActive(),
            eqType(typeEqual),
            containsKeyword(keywordLike),
            containsAnyTag(tagsIn)
        )
        .fetchOne();

    return new CursorResponse<>(
        contentIds,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount == null ? 0L : totalCount,
        SORT_BY_WATCHER_COUNT,
        sortDirection
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

  private BooleanExpression isActive() {
    return content.deletedAt.isNull();
  }

  private BooleanExpression containsKeyword(String keywordLike) {
    if (keywordLike == null || keywordLike.isBlank()) {
      return null;
    }

    String keyword = keywordLike.trim();
    if (!hasIndexableTrigram(keyword)) {
      String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
      return content.title.lower().indexOf(normalizedKeyword).goe(0)
          .or(content.description.lower().indexOf(normalizedKeyword).goe(0));
    }

    return content.title.containsIgnoreCase(keyword)
        .or(content.description.containsIgnoreCase(keyword));
  }

  private boolean hasIndexableTrigram(String keyword) {
    int consecutiveLettersOrDigits = 0;
    for (int offset = 0; offset < keyword.length(); ) {
      int codePoint = keyword.codePointAt(offset);
      if (Character.isLetterOrDigit(codePoint)) {
        consecutiveLettersOrDigits++;
        if (consecutiveLettersOrDigits >= MIN_INDEXABLE_TRIGRAM_LENGTH) {
          return true;
        }
      } else {
        consecutiveLettersOrDigits = 0;
      }
      offset += Character.charCount(codePoint);
    }
    return false;
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
    return createdAtCursorCondition(cursor, idAfter, sortDirection);
  }

  private BooleanExpression createdAtCursorCondition(
      String cursor,
      UUID idAfter,
      SortDirection sortDirection
  ) {
    Instant cursorCreatedAt = Instant.parse(cursor);
    if (idAfter == null) {
      return sortDirection == SortDirection.ASCENDING
          ? content.createdAt.gt(cursorCreatedAt)
          : content.createdAt.lt(cursorCreatedAt);
    }

    String operator = sortDirection == SortDirection.ASCENDING ? ">" : "<";
    return Expressions.booleanTemplate(
        "({0}, {1}) " + operator + " ({2}, {3})",
        content.createdAt,
        content.id,
        cursorCreatedAt,
        idAfter
    );
  }

  private BooleanExpression rateCursorCondition(
      String cursor,
      UUID idAfter,
      SortDirection sortDirection
  ) {
    double cursorRate = Double.parseDouble(cursor);
    if (idAfter == null) {
      return sortDirection == SortDirection.ASCENDING
          ? content.averageRating.gt(cursorRate)
          : content.averageRating.lt(cursorRate);
    }

    String operator = sortDirection == SortDirection.ASCENDING ? ">" : "<";
    return Expressions.booleanTemplate(
        "({0}, {1}) " + operator + " ({2}, {3})",
        content.averageRating,
        content.id,
        cursorRate,
        idAfter
    );
  }

  private BooleanExpression popularityCursorCondition(
      String cursor,
      UUID idAfter,
      SortDirection sortDirection,
      NumberExpression<Long> watcherCount
  ) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }

    String[] cursorParts = cursor.split(POPULARITY_CURSOR_DELIMITER, -1);
    if (cursorParts.length != 3) {
      throw new IllegalArgumentException("Invalid watcherCount cursor: " + cursor);
    }

    long cursorWatcherCount = Long.parseLong(cursorParts[0]);
    int cursorReviewCount = Integer.parseInt(cursorParts[1]);
    double cursorAverageRating = Double.parseDouble(cursorParts[2]);
    BooleanExpression sameWatcherCount = watcherCount.eq(cursorWatcherCount);
    BooleanExpression sameReviewCount = content.reviewCount.eq(cursorReviewCount);
    BooleanExpression sameAverageRating = content.averageRating.eq(cursorAverageRating);

    if (sortDirection == SortDirection.ASCENDING) {
      BooleanExpression afterCursor = watcherCount.gt(cursorWatcherCount)
          .or(sameWatcherCount.and(content.reviewCount.gt(cursorReviewCount)))
          .or(sameWatcherCount.and(sameReviewCount)
              .and(content.averageRating.gt(cursorAverageRating)));
      if (idAfter == null) {
        return afterCursor;
      }
      return afterCursor.or(
          sameWatcherCount
              .and(sameReviewCount)
              .and(sameAverageRating)
              .and(content.id.gt(idAfter))
      );
    }

    BooleanExpression beforeCursor = watcherCount.lt(cursorWatcherCount)
        .or(sameWatcherCount.and(content.reviewCount.lt(cursorReviewCount)))
        .or(sameWatcherCount.and(sameReviewCount)
            .and(content.averageRating.lt(cursorAverageRating)));
    if (idAfter == null) {
      return beforeCursor;
    }
    return beforeCursor.or(
        sameWatcherCount
            .and(sameReviewCount)
            .and(sameAverageRating)
            .and(content.id.lt(idAfter))
    );
  }

  private OrderSpecifier<?>[] createPopularityOrderSpecifiers(
      SortDirection sortDirection,
      NumberExpression<Long> watcherCount
  ) {
    OrderSpecifier<UUID> idOrder = createIdOrderSpecifier(sortDirection);
    if (sortDirection == SortDirection.ASCENDING) {
      return new OrderSpecifier<?>[]{
          watcherCount.asc(),
          content.reviewCount.asc(),
          content.averageRating.asc(),
          idOrder
      };
    }
    return new OrderSpecifier<?>[]{
        watcherCount.desc(),
        content.reviewCount.desc(),
        content.averageRating.desc(),
        idOrder
    };
  }

  private OrderSpecifier<?>[] createOrderSpecifiers(
      String sortBy,
      SortDirection sortDirection
  ) {
    OrderSpecifier<UUID> idOrder = createIdOrderSpecifier(sortDirection);
    if (SORT_BY_RATE.equals(sortBy)) {
      return new OrderSpecifier<?>[]{
          sortDirection == SortDirection.ASCENDING
              ? content.averageRating.asc()
              : content.averageRating.desc(),
          idOrder
      };
    }
    return new OrderSpecifier<?>[]{
        sortDirection == SortDirection.ASCENDING
            ? content.createdAt.asc()
            : content.createdAt.desc(),
        idOrder
    };
  }

  private OrderSpecifier<UUID> createIdOrderSpecifier(SortDirection sortDirection) {
    return sortDirection == SortDirection.ASCENDING
        ? content.id.asc()
        : content.id.desc();
  }

  private String nextCursor(Tuple row, String sortBy) {
    if (SORT_BY_RATE.equals(sortBy)) {
      return String.valueOf(row.get(content.averageRating));
    }
    Instant createdAt = row.get(content.createdAt);
    return createdAt == null ? null : createdAt.toString();
  }
}
