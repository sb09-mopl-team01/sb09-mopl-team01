package io.mopl.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieProvider {

  @Value("${jwt.refresh-token-validity-seconds}")
  private static int REFRESH_TOKEN_MAX_AGE;

  public ResponseCookie createRefreshTokenCookie(String refreshToken) {
    return ResponseCookie.from("REFRESH_TOKEN", refreshToken)
        .httpOnly(true)
        .secure(false) // https 적용 시 수정 필요
        .path("/")
        .maxAge(REFRESH_TOKEN_MAX_AGE)
        .sameSite("Strict")
        .build();
  }
}
