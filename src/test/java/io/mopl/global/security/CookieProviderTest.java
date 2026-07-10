package io.mopl.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

class CookieProviderTest {

  private final CookieProvider cookieProvider = new CookieProvider();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(cookieProvider, "ACCESS_TOKEN_MAX_AGE", 3600);
    ReflectionTestUtils.setField(cookieProvider, "REFRESH_TOKEN_MAX_AGE", 604800);
  }

  @Test
  @DisplayName("ACCESS_TOKEN 쿠키는 SSE 경로로만 제한한다")
  void createAccessTokenCookieLimitsPathToSse() {
    ResponseCookie cookie = cookieProvider.createAccessTokenCookie("access-token");

    assertThat(cookie.getName()).isEqualTo(CookieProvider.ACCESS_TOKEN_COOKIE_NAME);
    assertThat(cookie.getPath()).isEqualTo("/api/sse");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
  }

  @Test
  @DisplayName("REFRESH_TOKEN 쿠키는 전체 인증 경로에서 사용할 수 있게 유지한다")
  void createRefreshTokenCookieKeepsRootPath() {
    ResponseCookie cookie = cookieProvider.createRefreshTokenCookie("refresh-token");

    assertThat(cookie.getName()).isEqualTo(CookieProvider.REFRESH_TOKEN_COOKIE_NAME);
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
  }

  @Test
  @DisplayName("토큰 삭제 쿠키는 발급 쿠키와 동일한 경로로 만료한다")
  void deleteTokenCookiesUseMatchingPaths() {
    ResponseCookie accessTokenCookie = cookieProvider.deleteAccessTokenCookie();
    ResponseCookie refreshTokenCookie = cookieProvider.deleteRefreshTokenCookie();

    assertThat(accessTokenCookie.getPath()).isEqualTo("/api/sse");
    assertThat(refreshTokenCookie.getPath()).isEqualTo("/");
    assertThat(accessTokenCookie.getMaxAge().getSeconds()).isZero();
    assertThat(refreshTokenCookie.getMaxAge().getSeconds()).isZero();
  }
}
