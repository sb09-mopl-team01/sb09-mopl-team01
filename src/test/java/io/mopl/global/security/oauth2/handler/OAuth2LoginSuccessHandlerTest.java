package io.mopl.global.security.oauth2.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.global.security.CookieProvider;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.jwt.JwtProvider;
import io.mopl.global.security.oauth.handler.OAuth2LoginSuccessHandler;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

  @InjectMocks
  private OAuth2LoginSuccessHandler successHandler;

  @Mock private JwtProvider jwtProvider;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private CookieProvider cookieProvider;
  @Mock private Authentication authentication;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();

    ReflectionTestUtils.setField(successHandler, "frontendBaseUrl", "http://localhost:3000");
  }

  @Test
  @DisplayName("로그인 성공 시 - 토큰 발급, 쿠키 세팅, 리다이렉트가 정상 작동해야 한다")
  void onAuthenticationSuccess_WorksCorrectly() throws Exception {
    UUID userId = UUID.randomUUID();
    User mockUser = User.builder().email("test@test.com").build();
    ReflectionTestUtils.setField(mockUser, "id", userId);
    MoplUserDetails mockUserDetails = new MoplUserDetails(mockUser, Map.of());

    when(authentication.getPrincipal()).thenReturn(mockUserDetails);
    when(jwtProvider.generateAccessToken(mockUserDetails)).thenReturn("mock_access_token");
    when(jwtProvider.generateRefreshToken(any(), eq(userId.toString()))).thenReturn("mock_refresh_token");

    ResponseCookie mockCookie = ResponseCookie.from("test", "test").build();
    when(cookieProvider.createAccessTokenCookie(anyString())).thenReturn(mockCookie);
    when(cookieProvider.createRefreshTokenCookie(anyString())).thenReturn(mockCookie);

    successHandler.onAuthenticationSuccess(request, response, authentication);

    verify(refreshTokenRepository).save(userId, "mock_refresh_token");
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNotNull();
    assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
  }
}
