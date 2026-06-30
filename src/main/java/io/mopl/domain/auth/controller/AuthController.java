package io.mopl.domain.auth.controller;

import io.mopl.domain.auth.dto.LoginRequest;
import io.mopl.domain.auth.dto.LoginResponse;
import io.mopl.domain.auth.dto.TokenRefreshRequest;
import io.mopl.domain.auth.dto.TokenRefreshResult;
import io.mopl.domain.auth.repository.RefreshTokenMemoryRepository;
import io.mopl.domain.auth.service.AuthService;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.global.security.CookieProvider;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final CookieProvider cookieProvider;

  @GetMapping("/csrf-token")
  public ResponseEntity<Void> getCsrfToken() {

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      @CookieValue(name = "REFRESH_TOKEN", required = false) String currentRefreshToken) {

    try {
      TokenRefreshResult result = authService.refreshTokens(currentRefreshToken);

      ResponseCookie newRefreshTokenCookie = cookieProvider.createRefreshTokenCookie(result.newRefreshToken());

      TokenRefreshRequest tokenRefreshRequest = new TokenRefreshRequest(
          result.userDto(),
          result.newAccessToken()
      );

      return ResponseEntity.ok()
          .header(HttpHeaders.SET_COOKIE, newRefreshTokenCookie.toString())
          .body(tokenRefreshRequest);

    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }
  }
}