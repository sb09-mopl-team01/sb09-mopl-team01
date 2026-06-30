package io.mopl.domain.auth.controller;

import io.mopl.domain.auth.dto.LoginRequest;
import io.mopl.domain.auth.dto.LoginResponse;
import io.mopl.domain.auth.dto.TokenRefreshRequest;
import io.mopl.domain.auth.repository.RefreshTokenMemoryRepository;
import io.mopl.domain.auth.service.AuthService;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final JwtProvider jwtProvider;
  private final RefreshTokenMemoryRepository refreshTokenRepository;
  private final MoplUserDetailsService userDetailsService;
  private final UserMapper userMapper;

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      @CookieValue(name = "REFRESH_TOKEN", required = false) String currentRefreshToken,
      HttpServletResponse response) {

    if (currentRefreshToken == null || !jwtProvider.validateToken(currentRefreshToken)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 리프레시 토큰입니다.");
    }

    String email = jwtProvider.getUsername(currentRefreshToken);
    String savedToken = refreshTokenRepository.findByEmail(email).orElse("");
    if (!savedToken.equals(currentRefreshToken)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("만료되었거나 조작된 리프레시 토큰입니다.");
    }

    MoplUserDetails userDetails = (MoplUserDetails)userDetailsService.loadUserByUsername(email);

    String newAccessToken = jwtProvider.generateAccessToken(userDetails);
    String newRefreshToken = jwtProvider.generateRefreshToken(email);

    refreshTokenRepository.save(email, newRefreshToken);

    ResponseCookie newRefreshTokenCookie = ResponseCookie.from("REFRESH_TOKEN", newRefreshToken)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(604800)
        .sameSite("Strict")
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, newRefreshTokenCookie.toString());

    UserDto userDto = userMapper.toDto(userDetails.getUser());
    TokenRefreshRequest tokenRefreshRequest = new TokenRefreshRequest(
        userDto,
        newAccessToken
    );

    return ResponseEntity.ok(tokenRefreshRequest);
  }
}
