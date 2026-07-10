package io.mopl.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.global.security.CookieProvider;
import io.mopl.global.security.MoplUserDetailsService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtAuthenticationFilterTest {

  private final JwtProvider jwtProvider = org.mockito.Mockito.mock(JwtProvider.class);
  private final MoplUserDetailsService userDetailsService =
      org.mockito.Mockito.mock(MoplUserDetailsService.class);
  private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
      jwtProvider,
      userDetailsService,
      redisTemplate,
      new ObjectMapper()
  );

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("SSE 요청은 Authorization 헤더가 없으면 ACCESS_TOKEN 쿠키로 인증한다")
  void authenticatesSseRequestWithAccessTokenCookie() throws Exception {
    String token = "access-token";
    String email = "user@example.com";
    UUID userId = UUID.randomUUID();
    UserDetails userDetails = User.withUsername(email)
        .password("password")
        .authorities("ROLE_USER")
        .build();

    given(jwtProvider.validateToken(token)).willReturn(true);
    given(jwtProvider.getUsername(token)).willReturn(email);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(redisTemplate.hasKey("blacklist:access_token:" + userId)).willReturn(false);
    given(userDetailsService.loadUserByUsername(email)).willReturn(userDetails);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/sse");
    request.setCookies(new Cookie(CookieProvider.ACCESS_TOKEN_COOKIE_NAME, token));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isSameAs(userDetails);
  }

  @Test
  @DisplayName("SSE 외 요청은 ACCESS_TOKEN 쿠키 인증 fallback을 사용하지 않는다")
  void ignoresAccessTokenCookieForNonSseRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/notifications");
    request.setCookies(new Cookie(CookieProvider.ACCESS_TOKEN_COOKIE_NAME, "access-token"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
