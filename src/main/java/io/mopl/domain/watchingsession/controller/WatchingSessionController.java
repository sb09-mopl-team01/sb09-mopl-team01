package io.mopl.domain.watchingsession.controller;

import io.mopl.domain.watchingsession.dto.WatchingSessionDto;
import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WatchingSessionController {

  private final WatchingSessionService watchingSessionService;

  @GetMapping("/users/{watcherId}/watching-sessions")
  public ResponseEntity<WatchingSessionDto> findWatchingSessionsByWatcher(
      @PathVariable UUID watcherId
  ) {
    return ResponseEntity.ok(watchingSessionService.findByWatcher(watcherId));
  }

  @GetMapping("/contents/{contentId}/watching-sessions")
  public ResponseEntity<CursorResponse<WatchingSessionDto>> findWatchingSessionsByContent(
      @PathVariable UUID contentId,
      @RequestParam(required = false) String watcherNameLike,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      @RequestParam String sortBy,
      @RequestParam SortDirection sortDirection
  ) {
    return ResponseEntity.ok(watchingSessionService.findByContent(
        contentId,
        watcherNameLike,
        cursor,
        idAfter,
        limit,
        sortBy,
        sortDirection
    ));
  }
}
