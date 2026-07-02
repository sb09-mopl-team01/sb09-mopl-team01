package io.mopl.global.security.handler;

import static org.mockito.Mockito.verify;

import io.mopl.domain.auth.repository.RefreshTokenMemoryRepository;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.sse.SseNotificationService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class MoplLogoutHandlerTest {

  private final RefreshTokenMemoryRepository refreshTokenRepository =
      org.mockito.Mockito.mock(RefreshTokenMemoryRepository.class);
  private final SseNotificationService sseNotificationService =
      org.mockito.Mockito.mock(SseNotificationService.class);
  private final MoplLogoutHandler logoutHandler =
      new MoplLogoutHandler(refreshTokenRepository, sseNotificationService);

  @Test
  @DisplayName("로그아웃 시 refresh token과 SSE 연결을 정리한다")
  void logoutClearsRefreshTokenAndSseConnection() {
    UUID userId = UUID.randomUUID();
    MoplUserDetails userDetails = userDetails(userId);
    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );

    logoutHandler.logout(null, null, authentication);

    verify(refreshTokenRepository).deleteByEmail(userDetails.getUsername());
    verify(sseNotificationService).closeByReceiverId(userId);
  }

  private MoplUserDetails userDetails(UUID userId) {
    User user = User.builder()
        .email("user@example.com")
        .passwordHash("password")
        .name("사용자")
        .role(Role.USER)
        .build();
    ReflectionTestUtils.setField(user, "id", userId);
    return new MoplUserDetails(user);
  }
}
