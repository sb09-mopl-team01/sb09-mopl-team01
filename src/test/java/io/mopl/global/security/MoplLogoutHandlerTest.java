package io.mopl.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.global.security.handler.MoplLogoutHandler;
import io.mopl.global.sse.SseNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MoplLogoutHandlerTest {

  @InjectMocks
  private MoplLogoutHandler moplLogoutHandler;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private SseNotificationService sseNotificationService;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private Authentication authentication;

  @Test
  @DisplayName("인증 정보가 있을 경우 리프레시 토큰과 SSE 연결을 성공적으로 삭제한다")
  void logout_WithAuthentication_ClearsTokenAndSse() {
    UUID userId = UUID.randomUUID();
    MoplUserDetails userDetails = userDetails(userId);

    when(authentication.getPrincipal()).thenReturn(userDetails);

    moplLogoutHandler.logout(request, response, authentication);

    verify(refreshTokenRepository, times(1)).deleteByUserId(userId);
    verify(sseNotificationService, times(1)).closeByReceiverId(userId);
  }

  @Test
  @DisplayName("인증 정보가 없을 경우 아무 작업도 하지 않는다")
  void logout_WithoutAuthentication_DoesNothing() {
    moplLogoutHandler.logout(request, response, null);

    verify(refreshTokenRepository, never()).deleteByUserId(any(UUID.class));
    verify(sseNotificationService, never()).closeByReceiverId(any(UUID.class));
  }

  @Test
  @DisplayName("Principal이 MoplUserDetails 타입이 아닐 경우 토큰 삭제를 수행하지 않는다")
  void logout_WithInvalidPrincipal_DoesNothing() {
    when(authentication.getPrincipal()).thenReturn("anonymousUser");

    moplLogoutHandler.logout(request, response, authentication);

    verify(refreshTokenRepository, never()).deleteByUserId(any(UUID.class));
    verify(sseNotificationService, never()).closeByReceiverId(any(UUID.class));
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