package io.mopl.domain.content.repository.search;

import io.mopl.domain.content.document.ContentDocument;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentSearchRepositoryCustomImpl implements ContentSearchRepositoryCustom {

  private static final String SORT_BY_CREATED_AT = "createdAt";
  private static final String SORT_BY_RATE = "rate";
  private static final String SORT_BY_WATCHER_COUNT = "watcherCount";
  private static final String POPULARITY_CURSOR_DELIMITER = "\\|";

  private final ElasticsearchOperations elasticsearchOperations;

  @Override
  public CursorResponse<UUID> searchContentIdsByCursor(
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
    SortDirection resolvedDirection = sortDirection == null
        ? SortDirection.DESCENDING
        : sortDirection;
    SortOrder order = resolvedDirection == SortDirection.ASCENDING
        ? SortOrder.ASC
        : SortOrder.DESC;
    BoolQueryBuilder boolQuery = createQuery(typeEqual, keywordLike, tagsIn);
    NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
        .withQuery(boolQuery);
    addSorts(queryBuilder, resolvedSortBy, order);
    NativeSearchQuery searchQuery = queryBuilder.withMaxResults(limit + 1).build();
    List<Object> searchAfter = searchAfter(cursor, idAfter, resolvedSortBy);
    if (!searchAfter.isEmpty()) {
      searchQuery.setSearchAfter(searchAfter);
    }

    SearchHits<ContentDocument> searchHits = elasticsearchOperations.search(
        searchQuery,
        ContentDocument.class
    );
    List<ContentDocument> documents = new ArrayList<>(searchHits.stream()
        .map(SearchHit::getContent)
        .toList());

    boolean hasNext = documents.size() > limit;
    if (hasNext) {
      documents.remove(limit);
    }

    ContentDocument lastDocument = documents.isEmpty() ? null : documents.get(documents.size() - 1);
    String nextCursor = lastDocument != null
        ? cursorValue(lastDocument, resolvedSortBy)
        : null;
    UUID nextIdAfter = lastDocument != null ? lastDocument.getId() : null;
    List<UUID> contentIds = documents.stream().map(ContentDocument::getId).toList();
    long totalCount = elasticsearchOperations.count(
        new NativeSearchQueryBuilder().withQuery(boolQuery).build(),
        ContentDocument.class
    );

    return new CursorResponse<>(
        contentIds,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        resolvedSortBy,
        resolvedDirection
    );
  }

  private BoolQueryBuilder createQuery(
      ContentType typeEqual,
      String keywordLike,
      Collection<String> tagsIn
  ) {
    String keyword = keywordLike.trim();
    BoolQueryBuilder keywordQuery = QueryBuilders.boolQuery()
        .should(QueryBuilders.matchQuery("title", keyword))
        .should(QueryBuilders.matchQuery("description", keyword))
        .minimumShouldMatch(1);
    if (isInitialKeyword(keyword)) {
      keywordQuery.should(QueryBuilders.wildcardQuery("initials", "*" + keyword + "*"));
    }

    BoolQueryBuilder query = QueryBuilders.boolQuery().must(keywordQuery);

    if (typeEqual != null) {
      query.filter(QueryBuilders.termQuery("type", typeEqual.getValue()));
    }
    if (tagsIn != null && !tagsIn.isEmpty()) {
      query.filter(QueryBuilders.termsQuery("tags", tagsIn));
    }
    return query;
  }

  private List<Object> searchAfter(String cursor, UUID idAfter, String sortBy) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return List.of();
    }

    Object primarySortValue;
    if (SORT_BY_RATE.equals(sortBy)) {
      primarySortValue = Double.parseDouble(cursor);
    } else if (SORT_BY_WATCHER_COUNT.equals(sortBy)) {
      String[] cursorParts = cursor.split(POPULARITY_CURSOR_DELIMITER, -1);
      if (cursorParts.length != 2) {
        throw new IllegalArgumentException("Invalid watcherCount cursor: " + cursor);
      }
      return List.of(
          Integer.parseInt(cursorParts[0]),
          Double.parseDouble(cursorParts[1]),
          idAfter.toString()
      );
    } else {
      primarySortValue = Instant.parse(cursor).toEpochMilli();
    }
    return List.of(primarySortValue, idAfter.toString());
  }

  private String resolveSortBy(String sortBy) {
    if (sortBy == null || sortBy.isBlank()) {
      return SORT_BY_CREATED_AT;
    }
    if (SORT_BY_CREATED_AT.equals(sortBy)
        || SORT_BY_RATE.equals(sortBy)
        || SORT_BY_WATCHER_COUNT.equals(sortBy)) {
      return sortBy;
    }
    throw new IllegalArgumentException("Unsupported OpenSearch content sortBy: " + sortBy);
  }

  private void addSorts(
      NativeSearchQueryBuilder queryBuilder,
      String sortBy,
      SortOrder order
  ) {
    if (SORT_BY_WATCHER_COUNT.equals(sortBy)) {
      queryBuilder
          .withSort(SortBuilders.fieldSort("reviewCount").order(order))
          .withSort(SortBuilders.fieldSort("averageRating").order(order))
          .withSort(SortBuilders.fieldSort("id").order(order));
      return;
    }
    String sortField = SORT_BY_RATE.equals(sortBy) ? "averageRating" : "createdAt";
    queryBuilder
        .withSort(SortBuilders.fieldSort(sortField).order(order))
        .withSort(SortBuilders.fieldSort("id").order(order));
  }

  private String cursorValue(ContentDocument document, String sortBy) {
    if (SORT_BY_WATCHER_COUNT.equals(sortBy)) {
      return document.getReviewCount() + "|" + document.getAverageRating();
    }
    if (SORT_BY_RATE.equals(sortBy)) {
      return String.valueOf(document.getAverageRating());
    }
    return document.getCreatedAt().toString();
  }

  private boolean isInitialKeyword(String keyword) {
    return keyword.matches("^[ㄱ-ㅎ]+$");
  }
}
