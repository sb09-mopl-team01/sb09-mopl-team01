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
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentSearchRepositoryCustomImpl implements ContentSearchRepositoryCustom {

  private static final String SORT_BY_CREATED_AT = "createdAt";
  private static final String SORT_BY_RATE = "rate";

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
    Criteria criteria = createCriteria(typeEqual, keywordLike, tagsIn);
    CriteriaQuery query = new CriteriaQuery(criteria);
    Sort.Direction direction = resolvedDirection == SortDirection.ASCENDING
        ? Sort.Direction.ASC
        : Sort.Direction.DESC;
    String sortField = sortField(resolvedSortBy);

    query.addSort(Sort.by(direction, sortField).and(Sort.by(direction, "id")));
    query.setMaxResults(limit + 1);
    List<Object> searchAfter = searchAfter(cursor, idAfter, resolvedSortBy);
    if (!searchAfter.isEmpty()) {
      query.setSearchAfter(searchAfter);
    }

    SearchHits<ContentDocument> searchHits = elasticsearchOperations.search(
        query,
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
        new CriteriaQuery(createCriteria(typeEqual, keywordLike, tagsIn)),
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

  private Criteria createCriteria(
      ContentType typeEqual,
      String keywordLike,
      Collection<String> tagsIn
  ) {
    String keyword = keywordLike.trim();
    Criteria keywordCriteria = Criteria.or()
        .subCriteria(Criteria.where("title").matches(keyword))
        .subCriteria(Criteria.where("description").matches(keyword));
    Criteria criteria = Criteria.and().subCriteria(keywordCriteria);

    if (typeEqual != null) {
      criteria.subCriteria(Criteria.where("type").is(typeEqual.getValue()));
    }
    if (tagsIn != null && !tagsIn.isEmpty()) {
      criteria.subCriteria(Criteria.where("tags").in(tagsIn));
    }
    return criteria;
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
