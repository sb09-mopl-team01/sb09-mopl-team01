package io.mopl.domain.content.repository;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.Collection;
import java.util.UUID;

public interface ContentRepositoryCustom {

  CursorResponse<Content> findContentsByCursor(
      ContentType typeEqual,
      String keywordLike,
      Collection<String> tagsIn,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      SortDirection sortDirection
  );
}
