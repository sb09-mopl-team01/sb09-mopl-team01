package io.mopl.domain.watchingsession.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.user.entity.User;
import io.mopl.domain.watchingsession.service.WatchingSessionService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class WatchingSessionWebSocketControllerTest {

  private final WatchingSessionService watchingSessionService = mock(WatchingSessionService.class);
  private final WatchingSessionWebSocketController controller =
      new WatchingSessionWebSocketController(watchingSessionService);

  private UUID watcherId;
  private UUID contentId;
  private Principal principal;

  @BeforeEach
  void setUp() {
    watcherId = UUID.randomUUID();
    contentId = UUID.randomUUID();
    User watcher = User.builder()
        .email("watcher@example.com")
        .passwordHash("hash")
        .name("watcher")
        .build();
    ReflectionTestUtils.setField(watcher, "id", watcherId);
    MoplUserDetails userDetails = new MoplUserDetails(watcher);
    principal = new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );
  }

  @Test
  void enterStartsWatchingWithAuthenticatedUser() {
    controller.enter(contentId, principal);

    verify(watchingSessionService).startWatching(watcherId, contentId);
  }

  @Test
  void leaveEndsWatchingWithAuthenticatedUser() {
    controller.leave(contentId, principal);

    verify(watchingSessionService).endWatching(watcherId, contentId);
  }

  @Test
  void enterRequiresAuthenticationPrincipal() {
    assertThatThrownBy(() -> controller.enter(contentId, () -> "anonymous"))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
  }

  @Test
  void leaveRejectsUserDetailsWithoutUser() {
    Principal invalidPrincipal = new UsernamePasswordAuthenticationToken(
        new MoplUserDetails(null),
        null
    );

    assertThatThrownBy(() -> controller.leave(contentId, invalidPrincipal))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
  }
}
