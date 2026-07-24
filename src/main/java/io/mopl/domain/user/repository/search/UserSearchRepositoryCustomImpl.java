package io.mopl.domain.user.repository.search;

import io.mopl.domain.user.document.UserDocument;
import io.mopl.global.response.OpenSearchCursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserSearchRepositoryCustomImpl implements UserSearchRepositoryCustom {

  private final ElasticsearchOperations elasticsearchOperations;

  @Override
  public OpenSearchCursorResponse<UserDocument> searchUsersByCursor(
      String emailLike, String roleEqual, Boolean isLocked,
      List<Object> lastSortValues, int limit,
      String sortBy, SortDirection sortDirection) {

    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

    if (emailLike != null && !emailLike.isBlank()) {
      BoolQueryBuilder searchCriteria = QueryBuilders.boolQuery()
          .should(QueryBuilders.wildcardQuery("email.keyword", "*" + emailLike + "*").caseInsensitive(true))
          .should(QueryBuilders.wildcardQuery("name.keyword", "*" + emailLike + "*").caseInsensitive(true));

      boolQuery.must(searchCriteria);
    }

    if (roleEqual != null && !roleEqual.isBlank()) {
      boolQuery.must(QueryBuilders.termQuery("role", roleEqual.toUpperCase()));
    }
    if (isLocked != null) {
      boolQuery.must(QueryBuilders.termQuery("isLocked", isLocked));
    }

    Sort.Direction direction = (sortDirection == SortDirection.ASCENDING)
        ? Sort.Direction.ASC
        : Sort.Direction.DESC;

    String sortField = getSortField(sortBy);

    NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
        .withQuery(boolQuery)
        .withSort(Sort.by(direction, sortField).and(Sort.by(Sort.Direction.ASC, "id")))
        .withMaxResults(limit + 1)
        .build();

    if (lastSortValues != null && !lastSortValues.isEmpty()) {
      searchQuery.setSearchAfter(lastSortValues);
    }

    SearchHits<UserDocument> searchHits = elasticsearchOperations.search(searchQuery, UserDocument.class);
    List<UserDocument> contents = searchHits.stream()
        .map(SearchHit::getContent)
        .collect(Collectors.toList());

    boolean hasNext = contents.size() > limit;
    if (hasNext) {
      contents.remove(limit);
    }

    List<Object> nextSortValues = null;
    if (!contents.isEmpty() && searchHits.getSearchHits().size() > contents.size()) {
      SearchHit<UserDocument> lastHit = searchHits.getSearchHits().get(contents.size() - 1);
      nextSortValues = lastHit.getSortValues();
    }

    long totalCount = elasticsearchOperations.count(searchQuery, UserDocument.class);

    return new OpenSearchCursorResponse<>(
        contents, nextSortValues, hasNext, totalCount
    );
  }

  private String getSortField(String sortBy) {
    if ("name".equalsIgnoreCase(sortBy)) return "name.keyword";
    if ("email".equalsIgnoreCase(sortBy)) return "email.keyword";
    if ("isLocked".equalsIgnoreCase(sortBy)) return "isLocked";
    if ("role".equalsIgnoreCase(sortBy)) return "role";
    return "createdAt";
  }
}