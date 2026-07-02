package io.mopl.domain.watchingsession.controller;

import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WatchingSessionWebSocketController {

  private final WatchingSessionService watchingSessionService;

  @MessageMapping("/contents/{contentId}/watching-sessions/enter")
  public void enter(
      @DestinationVariable UUID contentId,
      Principal principal
  ) {
    UUID watcherId = resolveWatcherId(principal);
    watchingSessionService.startWatching(watcherId, contentId);
  }

  @MessageMapping("/contents/{contentId}/watching-sessions/leave")
  public void leave(
      @DestinationVariable UUID contentId,
      Principal principal
  ) {
    UUID watcherId = resolveWatcherId(principal);
    watchingSessionService.endWatching(watcherId, contentId);
  }

  private UUID resolveWatcherId(Principal principal) {
    if (principal instanceof Authentication authentication
        && authentication.getPrincipal() instanceof MoplUserDetails userDetails) {
      return userDetails.getUser().getId();
    }

    throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
  }
}
