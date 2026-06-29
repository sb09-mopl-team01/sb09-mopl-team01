package io.mopl.domain.auth.controller;

import io.mopl.domain.auth.dto.LoginRequest;
import io.mopl.domain.auth.dto.LoginResponse;
import io.mopl.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  @PostMapping(value ="/sign-in", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<LoginResponse> login(@ModelAttribute LoginRequest request) {
    LoginResponse response = authService.login(request.username(), request.password());

    return ResponseEntity.ok(response);
  }
}
