package io.mopl.domain.content.service;

import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.search.ContentSearchRepository;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSearchQueryService {

  private static final int MIN_OPENSEARCH_KEYWORD_LENGTH = 2;
  private static final int MAX_OPENSEARCH_KEYWORD_LENGTH = 20;

  private final ContentSearchRepository contentSearchRepository;

  public Optional<CursorResponse<UUID>> search(
      ContentType typeEqual,
      String keywordLike,
      Collection<String> tagsIn,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      SortDirection sortDirection
  ) {
    if (!supports(keywordLike, cursor, idAfter, sortBy)) {
      return Optional.empty();
    }

    try {
      return Optional.of(contentSearchRepository.searchContentIdsByCursor(
          typeEqual,
          keywordLike,
          tagsIn,
          cursor,
          idAfter,
          limit,
          sortBy,
          sortDirection
      ));
    } catch (RuntimeException e) {
      log.warn(
          "Content search fallback. operation=query, result=database, errorType={}",
          e.getClass().getSimpleName()
      );
      return Optional.empty();
    }
  }

  private boolean supports(
      String keywordLike,
      String cursor,
      UUID idAfter,
      String sortBy
  ) {
    if (keywordLike == null || keywordLike.isBlank()) {
      return false;
    }
    String keyword = keywordLike.trim();
    int keywordLength = keyword.codePointCount(0, keyword.length());
    boolean isSingleInitial = keywordLength == 1 && keyword.matches("^[ㄱ-ㅎ]$");
    if (!isSingleInitial && (keywordLength < MIN_OPENSEARCH_KEYWORD_LENGTH
        || keywordLength > MAX_OPENSEARCH_KEYWORD_LENGTH)) {
      return false;
    }
    return cursor == null || cursor.isBlank() || idAfter != null;
  }
}
