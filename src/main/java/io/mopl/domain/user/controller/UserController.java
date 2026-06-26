package io.mopl.domain.user.controller;

import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.ChangePasswordRequest;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.dto.request.UserLockUpdateRequest;
import io.mopl.domain.user.dto.request.UserRoleUpdateRequest;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.service.UserService;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping
  public ResponseEntity<UserDto> createUser(@RequestBody UserCreateRequest request) {
    UserDto response = userService.createUser(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

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
    CursorResponse<UserDto> response = userService.findUsers(
        emailLike, roleEqual, isLocked, cursor, idAfter, limit, sortBy, sortDirection
    );
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{userId}")
  public ResponseEntity<UserDto> findUser(@PathVariable UUID userId) {
    UserDto response = userService.findUser(userId);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{userId}")
  public ResponseEntity<UserDto> updateUser(
      @PathVariable UUID userId,
      @RequestPart("request") UserUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image
  ) {
    UserDto response = userService.updateProfile(userId, request, image);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{userId}/role")
  public ResponseEntity<Void> updateUserRole(
      @PathVariable UUID userId,
      @RequestBody UserRoleUpdateRequest request
  ) {
    userService.updateUserRole(userId, request);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{userId}/password")
  public ResponseEntity<Void> updateUserPassword(
      @PathVariable UUID userId,
      @RequestBody ChangePasswordRequest request
  ) {
    userService.changePassword(userId, request);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{userId}/locked")
  public ResponseEntity<Void> updateUserLocked(
      @PathVariable UUID userId,
      @RequestBody UserLockUpdateRequest request
  ) {
    userService.updateUserLockStatus(userId, request);
    return ResponseEntity.noContent().build();
  }
}
