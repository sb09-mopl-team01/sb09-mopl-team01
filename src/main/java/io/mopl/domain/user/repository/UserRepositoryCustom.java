package io.mopl.domain.user.repository;

import io.mopl.domain.user.entity.User;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.UUID;

public interface UserRepositoryCustom {
  CursorResponse<User> findUsersByCursor(
      String emailLike,
      String roleEqual,
      Boolean isLocked,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      SortDirection sortDirection
  );
}
