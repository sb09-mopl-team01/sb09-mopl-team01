package io.mopl.global.security.oauth.handler;

import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.global.security.CookieProvider;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.jwt.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtProvider jwtProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  private final CookieProvider cookieProvider;

  @Value("${mopl.frontend.base-url}")
  private String frontendBaseUrl;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException, ServletException {

    MoplUserDetails userDetails = (MoplUserDetails) authentication.getPrincipal();
    UUID userId = userDetails.getUser().getId();
    String email = userDetails.getUsername();

    String accessToken = jwtProvider.generateAccessToken(userDetails);
    String refreshToken = jwtProvider.generateRefreshToken(email, userId.toString());

    refreshTokenRepository.save(userId, refreshToken);

    ResponseCookie accessTokenCookie = cookieProvider.createAccessTokenCookie(accessToken);
    ResponseCookie refreshTokenCookie = cookieProvider.createRefreshTokenCookie(refreshToken);
    response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
    response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

    String redirectUrl = frontendBaseUrl + "/";
    getRedirectStrategy().sendRedirect(request, response, redirectUrl);
  }
}
