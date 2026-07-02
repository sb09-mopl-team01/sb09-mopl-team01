package io.mopl.global.sse;

import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class SseController {

  private final SseNotificationService sseNotificationService;

  @GetMapping(value = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> subscribe(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @RequestParam(name = "LastEventId", required = false) UUID lastEventId
  ) {
    if (userDetails == null) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    SseEmitter emitter = sseNotificationService.subscribe(userDetails.getUser().getId(), lastEventId);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .cacheControl(CacheControl.noCache())
        .header(HttpHeaders.CONNECTION, "keep-alive")
        .header("X-Accel-Buffering", "no")
        .body(emitter);
  }
}
