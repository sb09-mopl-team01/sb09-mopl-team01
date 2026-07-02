package io.mopl.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.global.security.MoplUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseControllerTest {

  private final SseNotificationService sseNotificationService =
      org.mockito.Mockito.mock(SseNotificationService.class);
  private final SseController sseController = new SseController(sseNotificationService);

  @Test
  @DisplayName("GET /api/sse - 요청자의 SSE 연결을 생성한다")
  void subscribe() {
    UUID receiverId = UUID.randomUUID();
    UUID lastEventId = UUID.randomUUID();
    MoplUserDetails userDetails = userDetails(receiverId);
    SseEmitter emitter = new SseEmitter();
    when(sseNotificationService.subscribe(receiverId, lastEventId)).thenReturn(emitter);

    ResponseEntity<SseEmitter> result = sseController.subscribe(userDetails, lastEventId);

    assertThat(result.getBody()).isSameAs(emitter);
    assertThat(result.getHeaders().getContentType().toString()).isEqualTo("text/event-stream");
    assertThat(result.getHeaders().getCacheControl()).isEqualTo(CacheControl.noCache().getHeaderValue());
    assertThat(result.getHeaders().getFirst(HttpHeaders.CONNECTION)).isEqualTo("keep-alive");
    assertThat(result.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
    verify(sseNotificationService).subscribe(receiverId, lastEventId);
  }

  private MoplUserDetails userDetails(UUID userId) {
    User user = User.builder()
        .email("user@example.com")
        .passwordHash("password")
        .name("사용자")
        .role(Role.USER)
        .build();
    ReflectionTestUtils.setField(user, "id", userId);
    return new MoplUserDetails(user);
  }
}
