package io.mopl.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.global.security.handler.LoginSuccessHandler;
import io.mopl.global.security.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;

import java.io.PrintWriter;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginSuccessHandlerTest {

  @InjectMocks
  private LoginSuccessHandler loginSuccessHandler;

  @Mock private ObjectMapper objectMapper;
  @Mock private JwtProvider jwtProvider;
  @Mock private UserMapper userMapper;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private CookieProvider cookieProvider;

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private Authentication authentication;
  @Mock private MoplUserDetails userDetails;
  @Mock private User user;
  @Mock private PrintWriter printWriter;

  @Test
  @DisplayName("로그인 성공 시 토큰 발급 및 응답 설정이 정상적으로 수행된다")
  void onAuthenticationSuccess_Success() throws Exception {
    UUID userId = UUID.randomUUID();
    String email = "test@example.com";
    String accessToken = "access-token";
    String refreshToken = "refresh-token";
    ResponseCookie accessTokenCookie = ResponseCookie.from("ACCESS_TOKEN", accessToken).build();
    ResponseCookie refreshTokenCookie = ResponseCookie.from("REFRESH_TOKEN", refreshToken).build();
    UserDto userDto = mock(UserDto.class);

    when(authentication.getPrincipal()).thenReturn(userDetails);
    when(userDetails.getUsername()).thenReturn(email);
    when(userDetails.getUser()).thenReturn(user);
    when(user.getId()).thenReturn(userId);

    when(jwtProvider.generateAccessToken(userDetails)).thenReturn(accessToken);
    when(jwtProvider.generateRefreshToken(email, userId.toString())).thenReturn(refreshToken);

    when(cookieProvider.createAccessTokenCookie(accessToken)).thenReturn(accessTokenCookie);
    when(cookieProvider.createRefreshTokenCookie(refreshToken)).thenReturn(refreshTokenCookie);
    when(userMapper.toDto(user)).thenReturn(userDto);
    when(response.getWriter()).thenReturn(printWriter);

    loginSuccessHandler.onAuthenticationSuccess(request, response, authentication);

    verify(refreshTokenRepository).save(userId, refreshToken);
    verify(response).addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
    verify(response).addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
    verify(response).setStatus(200);
    verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
    verify(objectMapper).writeValue(eq(printWriter), any());
  }
}
