package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.search.ContentSearchRepository;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentSearchQueryServiceTest {

  @Mock
  private ContentSearchRepository contentSearchRepository;

  private ContentSearchQueryService contentSearchQueryService;

  @BeforeEach
  void setUp() {
    contentSearchQueryService = new ContentSearchQueryService(contentSearchRepository);
  }

  @Test
  void usesOpenSearchForSupportedKeywordQuery() {
    UUID contentId = UUID.randomUUID();
    CursorResponse<UUID> expected = new CursorResponse<>(
        List.of(contentId), null, null, false, 1L, "createdAt", SortDirection.DESCENDING
    );
    given(contentSearchRepository.searchContentIdsByCursor(
        ContentType.MOVIE, "한국", List.of("드라마"), null, null, 10,
        "createdAt", SortDirection.DESCENDING
    )).willReturn(expected);

    Optional<CursorResponse<UUID>> result = contentSearchQueryService.search(
        ContentType.MOVIE, "한국", List.of("드라마"), null, null, 10,
        "createdAt", SortDirection.DESCENDING
    );

    assertThat(result).contains(expected);
  }

  @Test
  void fallsBackToDatabaseForUnsupportedKeywordAndWatcherCountQueries() {
    assertThat(contentSearchQueryService.search(
        null, " ", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    )).isEmpty();
    assertThat(contentSearchQueryService.search(
        null, "한", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    )).isEmpty();
    assertThat(contentSearchQueryService.search(
        null, "스무글자를초과하는검색어는데이터베이스조회로안전하게대체한다", null,
        null, null, 10, "createdAt", SortDirection.DESCENDING
    )).isEmpty();
    assertThat(contentSearchQueryService.search(
        null, "한국", null, null, null, 10, "watcherCount", SortDirection.DESCENDING
    )).isEmpty();

    verify(contentSearchRepository, never()).searchContentIdsByCursor(
        any(), any(), any(), any(), any(), any(Integer.class), any(), any()
    );
  }

  @Test
  void fallsBackToDatabaseWhenOpenSearchFails() {
    given(contentSearchRepository.searchContentIdsByCursor(
        null, "검색어", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    )).willThrow(new RuntimeException("opensearch unavailable"));

    Optional<CursorResponse<UUID>> result = contentSearchQueryService.search(
        null, "검색어", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    );

    assertThat(result).isEmpty();
  }
}
