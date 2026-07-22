package io.mopl.domain.user.repository.search;

import io.mopl.domain.user.document.UserDocument;
import io.mopl.global.response.OpenSearchCursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;

public interface UserSearchRepositoryCustom {
  OpenSearchCursorResponse<UserDocument> searchUsersByCursor(
      String emailLike, String roleEqual, Boolean isLocked,
      List<Object> lastSortValues, int limit,
      String sortBy, SortDirection sortDirection
  );
}
