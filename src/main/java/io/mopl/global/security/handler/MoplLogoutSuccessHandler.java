package io.mopl.global.security.handler;

import io.mopl.global.security.CookieProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MoplLogoutSuccessHandler implements LogoutSuccessHandler {

  private final CookieProvider cookieProvider;

  @Override
  public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

    response.addHeader(HttpHeaders.SET_COOKIE, cookieProvider.deleteAccessTokenCookie().toString());
    response.addHeader(HttpHeaders.SET_COOKIE, cookieProvider.deleteRefreshTokenCookie().toString());

    response.setStatus(HttpStatus.OK.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\": \"로그아웃 되었습니다.\"}");
  }
}
