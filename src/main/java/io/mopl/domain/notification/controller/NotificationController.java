package io.mopl.domain.notification.controller;

import io.mopl.domain.notification.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  @DeleteMapping("/{notificationId}")
  public ResponseEntity<Void> readNotification(@PathVariable UUID notificationId) {
    notificationService.readNotification(notificationId);
    return ResponseEntity.noContent().build();
  }
}
