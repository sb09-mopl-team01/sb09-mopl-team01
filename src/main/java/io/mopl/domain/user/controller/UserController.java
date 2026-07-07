package io.mopl.domain.user.controller;

import io.mopl.domain.auth.service.TempPasswordService;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.ChangePasswordRequest;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.dto.request.UserLockUpdateRequest;
import io.mopl.domain.user.dto.request.UserRoleUpdateRequest;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.service.UserService;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import io.mopl.global.security.MoplUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreateRequest request) {
    UserDto response = userService.createUser(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<CursorResponse<UserDto>> findUsers(
      @RequestParam(required = false) String emailLike,
      @RequestParam(required = false) String roleEqual,
      @RequestParam(required = false) Boolean isLocked,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      @RequestParam String sortBy,
      @RequestParam SortDirection sortDirection
  ) {
    log.debug("User Multiple Read Requested. emailLike={}, roleEqual={}, isLocked={}, cursor={}, idAfter={}, limit={}, sortBy={}, sortDirection={}",
        emailLike, roleEqual, isLocked, cursor, idAfter, limit, sortBy, sortDirection);
    CursorResponse<UserDto> response = userService.findUsers(
        emailLike, roleEqual, isLocked, cursor, idAfter, limit, sortBy, sortDirection
    );
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{userId}")
  public ResponseEntity<UserDto> findUser(@PathVariable UUID userId) {
    log.debug("User Single Read Requested. id={}", userId);
    UserDto response = userService.findUser(userId);
    return ResponseEntity.ok(response);
  }

  @PreAuthorize("#userId == authentication.principal.user.id or hasRole('ADMIN')")
  @PatchMapping(value ="/{userId}" , consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UserDto> updateUser(
      @PathVariable UUID userId,
      @Valid @RequestPart("request") UserUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image
  ) {
    log.debug("User Update Profile Requested. id={}", userId);
    UserDto response = userService.updateProfile(userId, request, image);
    return ResponseEntity.ok(response);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{userId}/role")
  public ResponseEntity<Void> updateUserRole(
      @PathVariable UUID userId,
      @Valid @RequestBody UserRoleUpdateRequest request,
      @AuthenticationPrincipal MoplUserDetails userDetails
  ) {
    log.debug("User Update Role Requested. id={}", userId);
    userService.updateUserRole(userId, request);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("#userId== authentication.principal.user.id or hasRole('ADMIN')")
  @PatchMapping("/{userId}/password")
  public ResponseEntity<Void> updateUserPassword(
      @PathVariable UUID userId,
      @Valid @RequestBody ChangePasswordRequest request,
      @AuthenticationPrincipal MoplUserDetails userDetails
  ) {
    log.debug("User Update Password Requested. id={}", userId);
    userService.changePassword(userId, request);

    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{userId}/locked")
  public ResponseEntity<Void> updateUserLocked(
      @PathVariable UUID userId,
      @Valid @RequestBody UserLockUpdateRequest request
  ) {
    log.debug("User Update LockStatus Requested. id={}", userId);
    userService.updateUserLockStatus(userId, request);
    return ResponseEntity.noContent().build();
  }
}
