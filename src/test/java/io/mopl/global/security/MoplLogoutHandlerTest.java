package io.mopl.global.security;



import static org.mockito.Mockito.*;

import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.global.security.handler.MoplLogoutHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class MoplLogoutHandlerTest {

  @InjectMocks
  private MoplLogoutHandler moplLogoutHandler;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;
  @Mock
  private HttpServletRequest request;
  @Mock
  private HttpServletResponse response;
  @Mock
  private Authentication authentication;

  @Test
  @DisplayName("인증 정보가 있을 경우 리프레시 토큰 삭제")
  void logout_WithAuthentication_DeletesToken() {
    String email = "test@example.com";
    when(authentication.getName()).thenReturn(email);

    moplLogoutHandler.logout(request, response, authentication);

    verify(refreshTokenRepository, times(1)).deleteByEmail(email);
  }

  @Test
  @DisplayName("인증 정보가 없을 경우")
  void logout_WithoutAuthentication_DoesNothing() {
    moplLogoutHandler.logout(request, response, null);

    verify(refreshTokenRepository, never()).deleteByEmail(anyString());
  }
}