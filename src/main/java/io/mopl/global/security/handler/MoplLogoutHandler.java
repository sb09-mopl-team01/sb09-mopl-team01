package io.mopl.global.security.handler;

import io.mopl.domain.auth.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MoplLogoutHandler implements LogoutHandler {

  private final RefreshTokenRepository refreshTokenRepository;

  @Override
  public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    if (authentication != null) {
      String email = authentication.getName();
      refreshTokenRepository.deleteByEmail(email);
    }
  }
}
