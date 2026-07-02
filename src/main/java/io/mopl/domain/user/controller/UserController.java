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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  private final TempPasswordService tempPasswordService;

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
    log.info("[사용자 관리] 사용자 다건 조회 요청 수신. emailLike={}, roleEqual={}, isLocked={}"
            + ", idAfter={}, limit={}, sortBy={}, sortDirection={}", emailLike, roleEqual, isLocked
        , idAfter, limit, sortBy, sortDirection);
    CursorResponse<UserDto> response = userService.findUsers(
        emailLike, roleEqual, isLocked, cursor, idAfter, limit, sortBy, sortDirection
    );
    log.debug("[사용자 관리] 사용자 다건 조회 요청 처리 완료. emailLike={}, roleEqual={}, isLocked={}"
            + ", idAfter={}, limit={}, sortBy={}, sortDirection={}", emailLike, roleEqual, isLocked
        , idAfter, limit, sortBy, sortDirection);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{userId}")
  public ResponseEntity<UserDto> findUser(@PathVariable UUID userId) {
    log.info("[사용자 관리] 사용자 조회 요청 수신. id={}", userId);
    UserDto response = userService.findUser(userId);
    log.debug("[사용자 관리] 사용자 조회 요청 처리 완료. id={}", userId);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{userId}")
  public ResponseEntity<UserDto> updateUser(
      @PathVariable UUID userId,
      @RequestPart("request") UserUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image
  ) {
    log.info("[사용자 관리] 사용자 프로필 수정 요청 수신. id={}", userId);
    UserDto response = userService.updateProfile(userId, request, image);
    log.debug("[사용자 관리] 사용자 프로필 수정 요청 처리 완료. id={}", userId);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{userId}/role")
  public ResponseEntity<Void> updateUserRole(
      @PathVariable UUID userId,
      @RequestBody UserRoleUpdateRequest request
  ) {
    log.info("[사용자 관리] 사용자 권한 변경 요청 수신. id={}", userId);
    userService.updateUserRole(userId, request);
    log.debug("[사용자 관리] 사용자 권한 변경 요청 처리 완료. id={}", userId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{userId}/password")
  public ResponseEntity<Void> updateUserPassword(
      @PathVariable UUID userId,
      @RequestBody ChangePasswordRequest request,
      @AuthenticationPrincipal MoplUserDetails userDetails
  ) {
    log.info("[사용자 관리] 사용자 비밀번호 수정 요청 수신. id={}", userId);
    userService.changePassword(userId, request);

    if (userDetails != null) {
      tempPasswordService.deleteTempPassword(userDetails.getUsername());
    }

    log.debug("[사용자 관리] 사용자 비밀번호 수정 요청 처리 완료. id={}", userId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{userId}/locked")
  public ResponseEntity<Void> updateUserLocked(
      @PathVariable UUID userId,
      @RequestBody UserLockUpdateRequest request
  ) {
    log.info("[사용자 관리] 사용자 잠금 요청 수신. id={}", userId);
    userService.updateUserLockStatus(userId, request);
    log.debug("[사용자 관리] 사용자 잠금 요청 처리 완료. id={}", userId);
    return ResponseEntity.noContent().build();
  }
}
