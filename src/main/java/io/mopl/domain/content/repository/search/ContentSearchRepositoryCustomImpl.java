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
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;

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
    SortOrder order = (sortDirection == null || sortDirection == SortDirection.DESCENDING)
        ? SortOrder.DESC
        : SortOrder.ASC;

    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

    if (keywordLike != null && !keywordLike.isBlank()) {
      String keyword = keywordLike.trim();
      BoolQueryBuilder keywordCriteria = QueryBuilders.boolQuery()
          .should(QueryBuilders.matchQuery("title", keyword))
          .should(QueryBuilders.matchQuery("description", keyword))
          .should(QueryBuilders.wildcardQuery("initials", "*" + keyword + "*"));

      boolQuery.must(keywordCriteria);
    }

    if (typeEqual != null) {
      boolQuery.must(QueryBuilders.termQuery("type", typeEqual.getValue()));
    }
    if (tagsIn != null && !tagsIn.isEmpty()) {
      boolQuery.must(QueryBuilders.termsQuery("tags", tagsIn));
    }

    if (boolQuery.must().isEmpty() && boolQuery.filter().isEmpty() && boolQuery.should().isEmpty()) {
      boolQuery.must(QueryBuilders.matchAllQuery());
    }

    String sortField = sortField(resolvedSortBy);

    NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
        .withQuery(boolQuery)
        .withSort(SortBuilders.fieldSort(sortField).order(order))
        .withSort(SortBuilders.fieldSort("id").order(SortOrder.ASC))
        .withMaxResults(limit + 1)
        .build();

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

    long totalCount = elasticsearchOperations.count(searchQuery, ContentDocument.class);

    return new CursorResponse<>(
        contentIds,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        resolvedSortBy,
        (order == SortOrder.ASC) ? SortDirection.ASCENDING : SortDirection.DESCENDING
    );
  }

  private List<Object> searchAfter(String cursor, UUID idAfter, String sortBy) {
    if (cursor == null || cursor.isBlank() || idAfter == null) {
      return List.of();
    }

    Object primarySortValue = SORT_BY_RATE.equals(sortBy)
        ? Double.parseDouble(cursor)
        : Instant.parse(cursor).toEpochMilli();
    return List.of(primarySortValue, idAfter.toString());
  }

  private String resolveSortBy(String sortBy) {
    if (sortBy == null || sortBy.isBlank()) {
      return SORT_BY_CREATED_AT;
    }
    if (SORT_BY_CREATED_AT.equals(sortBy) || SORT_BY_RATE.equals(sortBy)) {
      return sortBy;
    }
    if (SORT_BY_WATCHER_COUNT.equals(sortBy)) {
      return SORT_BY_CREATED_AT;
    }
    throw new IllegalArgumentException("Unsupported OpenSearch content sortBy: " + sortBy);
  }

  private String sortField(String sortBy) {
    return SORT_BY_RATE.equals(sortBy) ? "averageRating" : "createdAt";
  }

  private String cursorValue(ContentDocument document, String sortBy) {
    return SORT_BY_RATE.equals(sortBy)
        ? String.valueOf(document.getAverageRating())
        : document.getCreatedAt().toString();
  }
}