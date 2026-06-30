package io.mopl.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.auth.dto.LoginResponse;
import io.mopl.domain.auth.repository.RefreshTokenMemoryRepository;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.jwt.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final ObjectMapper objectMapper;
  private final JwtProvider jwtProvider;
  private final UserMapper userMapper;

  private final RefreshTokenMemoryRepository refreshTokenRepository;

  @Value("${jwt.refresh-token-validity-seconds}")
  private long refreshTokenValiditySeconds;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException, ServletException {

    log.debug("[LoginSuccessHandler] 로그인 성공 처리 완료");

    MoplUserDetails userDetails = (MoplUserDetails) authentication.getPrincipal();
    String email = userDetails.getUsername();

    String accessToken = jwtProvider.generateAccessToken(userDetails);
    String refreshToken = jwtProvider.generateRefreshToken(email);

    // 임시로 서버 메모리에 저장
    refreshTokenRepository.save(email, refreshToken);

    ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshToken)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(refreshTokenValiditySeconds)
        .sameSite("Strict")
        .build();

    UserDto userDto = userMapper.toDto(userDetails.getUser());
    LoginResponse loginResponse = new LoginResponse(userDto, accessToken);

    response.setStatus(HttpStatus.OK.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    objectMapper.writeValue(response.getWriter(), loginResponse);
  }
}
