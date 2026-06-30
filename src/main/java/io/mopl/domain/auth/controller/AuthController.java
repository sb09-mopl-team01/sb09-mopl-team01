package io.mopl.domain.auth.controller;

import io.mopl.domain.auth.dto.LoginRequest;
import io.mopl.domain.auth.dto.LoginResponse;
import io.mopl.domain.auth.dto.TokenRefreshRequest;
import io.mopl.domain.auth.dto.TokenRefreshResult;
import io.mopl.domain.auth.repository.RefreshTokenMemoryRepository;
import io.mopl.domain.auth.service.AuthService;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @GetMapping("/csrf-token")
  public ResponseEntity<Void> getCsrfToken(HttpServletRequest request) {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

    if (csrfToken != null) {
      csrfToken.getToken();
    }

    return ResponseEntity.ok().build();
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      @CookieValue(name = "REFRESH_TOKEN", required = false) String currentRefreshToken,
      HttpServletResponse response) {

    try {
      TokenRefreshResult result = authService.refreshTokens(currentRefreshToken);

      ResponseCookie newRefreshTokenCookie = ResponseCookie.from("REFRESH_TOKEN", result.newRefreshToken())
          .httpOnly(true)
          .secure(true)
          .path("/")
          .maxAge(604800)
          .sameSite("Strict")
          .build();
      response.addHeader(HttpHeaders.SET_COOKIE, newRefreshTokenCookie.toString());

      TokenRefreshRequest tokenRefreshRequest = new TokenRefreshRequest(
          result.userDto(),
          result.newAccessToken()
      );

      return ResponseEntity.ok(tokenRefreshRequest);

    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }
  }
}
