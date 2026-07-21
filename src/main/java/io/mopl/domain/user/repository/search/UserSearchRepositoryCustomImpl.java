package io.mopl.domain.user.repository.search;

import io.mopl.domain.user.document.UserDocument;
import io.mopl.global.response.OpenSearchCursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import java.util.stream.Collectors;
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
public class UserSearchRepositoryCustomImpl implements UserSearchRepositoryCustom {

  private final ElasticsearchOperations elasticsearchOperations;

  @Override
  public OpenSearchCursorResponse<UserDocument> searchUsersByCursor(
      String emailLike, String roleEqual, Boolean isLocked,
      List<Object> lastSortValues, int limit,
      String sortBy, SortDirection sortDirection) {

    Criteria criteria = new Criteria();

    if (emailLike != null && !emailLike.isBlank()) {
      criteria = criteria.and("email").contains(emailLike);
    }
    if (roleEqual != null && !roleEqual.isBlank()) {
      criteria = criteria.and("role").is(roleEqual.toUpperCase());
    }
    if (isLocked != null) {
      criteria = criteria.and("locked").is(isLocked);
    }

    CriteriaQuery criteriaQuery = new CriteriaQuery(criteria);

    Sort.Direction direction = (sortDirection == SortDirection.ASCENDING)
        ? Sort.Direction.ASC
        : Sort.Direction.DESC;

    String sortField = getSortField(sortBy);

    criteriaQuery.addSort(Sort.by(direction, sortField).and(Sort.by(Sort.Direction.ASC, "id")));

    criteriaQuery.setMaxResults(limit + 1);

    if (lastSortValues != null && !lastSortValues.isEmpty()) {
      criteriaQuery.setSearchAfter(lastSortValues);
    }

    SearchHits<UserDocument> searchHits = elasticsearchOperations.search(criteriaQuery, UserDocument.class);
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

    long totalCount = elasticsearchOperations.count(criteriaQuery, UserDocument.class);

    return new OpenSearchCursorResponse<>(
        contents, nextSortValues, hasNext, totalCount
    );
  }

  private String getSortField(String sortBy) {
    if ("name".equalsIgnoreCase(sortBy)) return "name";
    if ("email".equalsIgnoreCase(sortBy)) return "email.keyword";
    if ("isLocked".equalsIgnoreCase(sortBy)) return "isLocked";
    if ("role".equalsIgnoreCase(sortBy)) return "role";
    return "createdAt";
  }
}
