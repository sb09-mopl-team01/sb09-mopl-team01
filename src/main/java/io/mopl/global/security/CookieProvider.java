package io.mopl.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieProvider {

  public static final String ACCESS_TOKEN_COOKIE_NAME = "ACCESS_TOKEN";
  public static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";
  private static final String ACCESS_TOKEN_COOKIE_PATH = "/api/sse";
  private static final String REFRESH_TOKEN_COOKIE_PATH = "/";

  @Value("${jwt.access-token-validity-seconds}")
  private int ACCESS_TOKEN_MAX_AGE;

  @Value("${jwt.refresh-token-validity-seconds}")
  private int REFRESH_TOKEN_MAX_AGE;

  public ResponseCookie createAccessTokenCookie(String accessToken) {
    return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, accessToken)
        .httpOnly(true)
        .secure(true) // https 적용 시 수정 필요
        .path(ACCESS_TOKEN_COOKIE_PATH)
        .maxAge(ACCESS_TOKEN_MAX_AGE)
        .sameSite("Lax")
        .build();
  }

  public ResponseCookie createRefreshTokenCookie(String refreshToken) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
        .httpOnly(true)
        .secure(true) // https 적용 시 수정 필요
        .path(REFRESH_TOKEN_COOKIE_PATH)
        .maxAge(REFRESH_TOKEN_MAX_AGE)
        .sameSite("Lax")
        .build();
  }

  public ResponseCookie deleteAccessTokenCookie() {
    return deleteCookie(ACCESS_TOKEN_COOKIE_NAME, ACCESS_TOKEN_COOKIE_PATH);
  }

  public ResponseCookie deleteRefreshTokenCookie() {
    return deleteCookie(REFRESH_TOKEN_COOKIE_NAME, REFRESH_TOKEN_COOKIE_PATH);
  }

  private ResponseCookie deleteCookie(String name, String path) {
    return ResponseCookie.from(name, "")
        .httpOnly(true)
        .secure(true) // https 적용 시 수정 필요
        .path(path)
        .maxAge(0)
        .sameSite("Lax")
        .build();
  }
}
