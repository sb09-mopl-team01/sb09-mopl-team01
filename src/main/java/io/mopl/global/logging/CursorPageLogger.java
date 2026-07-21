package io.mopl.global.logging;

import io.mopl.global.response.SortDirection;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CursorPageLogger {

  /**
   * 도메인별 커서 페이지네이션 로그 포맷을 맞추기 위한 공통 로거입니다.
   */
  public static void logNextPageRequest(
      String domain,
      Object ownerId,
      Object cursor,
      UUID idAfter,
      int limit,
      SortDirection sortDirection
  ) {
    if (cursor == null) {
      return;
    }

    log.info(
        "Cursor next page requested. domain={}, ownerId={}, cursor={}, idAfter={}, limit={}, sortDirection={}",
        domain,
        ownerId,
        cursor,
        idAfter,
        limit,
        sortDirection
    );
  }

  public static void logNextPageResult(
      String domain,
      Object ownerId,
      Object cursor,
      UUID idAfter,
      int resultSize,
      boolean hasNext,
      Object nextCursor,
      UUID nextIdAfter
  ) {
    if (cursor == null) {
      return;
    }

    log.info(
        "Cursor next page completed. domain={}, ownerId={}, cursor={}, idAfter={}, resultSize={}, hasNext={}, nextCursor={}, nextIdAfter={}",
        domain,
        ownerId,
        cursor,
        idAfter,
        resultSize,
        hasNext,
        nextCursor,
        nextIdAfter
    );
  }
}
