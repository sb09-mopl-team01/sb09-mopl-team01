package io.mopl.domain.notification.controller;

import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.service.NotificationService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import io.mopl.global.security.MoplUserDetails;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  @GetMapping
  public ResponseEntity<CursorResponse<NotificationDto>> getNotifications(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      @RequestParam String sortBy,
      @RequestParam SortDirection sortDirection
  ) {
    if (userDetails == null) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    UUID receiverId = resolveUserId(userDetails);

    CursorResponse<NotificationDto> response = notificationService.getNotifications(
        receiverId,
        cursor,
        idAfter,
        limit,
        sortBy,
        sortDirection
    );
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{notificationId}")
  public ResponseEntity<Void> readNotification(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @PathVariable UUID notificationId
  ) {
    if (userDetails == null) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    UUID receiverId = resolveUserId(userDetails);

    notificationService.readNotification(notificationId, receiverId);
    return ResponseEntity.noContent().build();
  }

  private UUID resolveUserId(MoplUserDetails userDetails) {
    if (userDetails.getUser() == null || userDetails.getUser().getId() == null) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    return userDetails.getUser().getId();
  }
}
