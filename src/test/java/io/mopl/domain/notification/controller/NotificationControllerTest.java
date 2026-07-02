package io.mopl.domain.notification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mopl.domain.notification.dto.NotificationDto;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.service.NotificationService;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = NotificationController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class}
)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private NotificationService notificationService;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private MoplUserDetailsService userDetailsService;

  @Test
  @DisplayName("GET /api/notifications - 요청자의 알림 목록 조회")
  void getNotifications() throws Exception {
    UUID receiverId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    String cursor = "2026-07-02T10:15:30Z";
    MoplUserDetails userDetails = userDetails(receiverId);
    NotificationDto notification = new NotificationDto(
        UUID.randomUUID(),
        Instant.parse("2026-07-02T10:16:00Z"),
        receiverId,
        "새 알림",
        "알림 내용",
        NotificationLevel.INFO,
        false
    );
    CursorResponse<NotificationDto> response = new CursorResponse<>(
        List.of(notification),
        "2026-07-02T10:16:00Z",
        notification.id(),
        true,
        2,
        "createdAt",
        SortDirection.DESCENDING
    );
    given(notificationService.getNotifications(
        eq(receiverId),
        eq(cursor),
        eq(idAfter),
        eq(10),
        eq("createdAt"),
        eq(SortDirection.DESCENDING)
    )).willReturn(response);

    mockMvc.perform(get("/api/notifications")
            .with(user(userDetails))
            .param("cursor", cursor)
            .param("idAfter", idAfter.toString())
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isOk());

    verify(notificationService).getNotifications(
        receiverId,
        cursor,
        idAfter,
        10,
        "createdAt",
        SortDirection.DESCENDING
    );
  }

  @Test
  @DisplayName("DELETE /api/notifications/{notificationId} - 알림 읽음 처리")
  void readNotification() throws Exception {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();

    mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
            .with(user(userDetails(receiverId))))
        .andExpect(status().isNoContent());

    verify(notificationService).readNotification(notificationId, receiverId);
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
