package io.mopl.domain.user.repository.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.user.document.UserDocument;
import io.mopl.global.response.OpenSearchCursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;

@ExtendWith(MockitoExtension.class)
class UserSearchRepositoryCustomImplTest {

  @InjectMocks
  private UserSearchRepositoryCustomImpl userSearchRepositoryCustomImpl;

  @Mock
  private ElasticsearchOperations elasticsearchOperations;

  @Test
  @DisplayName("필터 조건 없이 기본 검색 시 정상적으로 데이터를 반환한다")
  void searchUsersByCursor_NoFilters_Success() {
    int limit = 10;
    SearchHits<UserDocument> searchHitsMock = mock(SearchHits.class);
    SearchHit<UserDocument> searchHitMock = mock(SearchHit.class);
    UserDocument userDocument = UserDocument.builder()
        .id(UUID.randomUUID())
        .email("test@example.com")
        .name("홍길동")
        .build();

    given(searchHitMock.getContent()).willReturn(userDocument);
    given(searchHitsMock.stream()).willReturn(Stream.of(searchHitMock));
    given(searchHitsMock.getSearchHits()).willReturn(List.of(searchHitMock));

    given(elasticsearchOperations.search(any(CriteriaQuery.class), eq(UserDocument.class)))
        .willReturn(searchHitsMock);
    given(elasticsearchOperations.count(any(CriteriaQuery.class), eq(UserDocument.class)))
        .willReturn(1L);

    OpenSearchCursorResponse<UserDocument> response = userSearchRepositoryCustomImpl.searchUsersByCursor(
        null, null, null, null, limit, "createdAt", SortDirection.DESCENDING
    );

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).getEmail()).isEqualTo("test@example.com");
    assertThat(response.hasNext()).isFalse();
    assertThat(response.totalCount()).isEqualTo(1L);

    ArgumentCaptor<CriteriaQuery> queryCaptor = ArgumentCaptor.forClass(CriteriaQuery.class);
    verify(elasticsearchOperations).search(queryCaptor.capture(), eq(UserDocument.class));
    assertThat(queryCaptor.getValue().getMaxResults()).isEqualTo(limit + 1);
  }

  @Test
  @DisplayName("조회된 데이터가 limit보다 많을 경우, 초과된 데이터를 자르고 hasNext=true를 반환한다")
  void searchUsersByCursor_HasNext_True() {
    int limit = 1;
    SearchHits<UserDocument> searchHitsMock = mock(SearchHits.class);
    SearchHit<UserDocument> searchHitMock1 = mock(SearchHit.class);
    SearchHit<UserDocument> searchHitMock2 = mock(SearchHit.class);

    UserDocument doc1 = UserDocument.builder().id(UUID.randomUUID()).build();
    UserDocument doc2 = UserDocument.builder().id(UUID.randomUUID()).build();

    given(searchHitMock1.getContent()).willReturn(doc1);
    given(searchHitMock1.getSortValues()).willReturn(List.of("1000", doc1.getId().toString()));
    given(searchHitMock2.getContent()).willReturn(doc2);

    given(searchHitsMock.stream()).willReturn(Stream.of(searchHitMock1, searchHitMock2));
    given(searchHitsMock.getSearchHits()).willReturn(List.of(searchHitMock1, searchHitMock2));

    given(elasticsearchOperations.search(any(CriteriaQuery.class), eq(UserDocument.class)))
        .willReturn(searchHitsMock);
    given(elasticsearchOperations.count(any(CriteriaQuery.class), eq(UserDocument.class)))
        .willReturn(2L);

    OpenSearchCursorResponse<UserDocument> response = userSearchRepositoryCustomImpl.searchUsersByCursor(
        "example", "USER", false, null, limit, "createdAt", SortDirection.DESCENDING
    );

    assertThat(response.content()).hasSize(1);
    assertThat(response.hasNext()).isTrue();
    assertThat(response.totalCount()).isEqualTo(2L);
    assertThat(response.nextSortValues()).containsExactly("1000", doc1.getId().toString());
  }

  @Test
  @DisplayName("lastSortValues가 존재하면 CriteriaQuery에 searchAfter가 정상적으로 적용된다")
  void searchUsersByCursor_WithLastSortValues_AppliesSearchAfter() {
    int limit = 10;
    List<Object> lastSortValues = List.of("1700000000000", "some-uuid");

    SearchHits<UserDocument> searchHitsMock = mock(SearchHits.class);
    given(searchHitsMock.stream()).willReturn(Stream.empty());

    given(elasticsearchOperations.search(any(CriteriaQuery.class), eq(UserDocument.class)))
        .willReturn(searchHitsMock);
    given(elasticsearchOperations.count(any(CriteriaQuery.class), eq(UserDocument.class)))
        .willReturn(0L);

    userSearchRepositoryCustomImpl.searchUsersByCursor(
        null, null, null, lastSortValues, limit, "name", SortDirection.ASCENDING
    );

    ArgumentCaptor<CriteriaQuery> queryCaptor = ArgumentCaptor.forClass(CriteriaQuery.class);
    verify(elasticsearchOperations).search(queryCaptor.capture(), eq(UserDocument.class));

    assertThat(queryCaptor.getValue().getSearchAfter()).isEqualTo(lastSortValues);
  }
}