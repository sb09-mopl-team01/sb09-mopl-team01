package io.mopl.domain.notification.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mopl.domain.notification.service.NotificationService;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
  @DisplayName("DELETE /api/notifications/{notificationId} - 알림 읽음 처리")
  void readNotification() throws Exception {
    UUID notificationId = UUID.randomUUID();

    mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId))
        .andExpect(status().isNoContent());

    verify(notificationService).readNotification(notificationId);
  }
}
