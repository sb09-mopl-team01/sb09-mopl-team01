package io.mopl.global.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mopl.domain.content.batch.ContentExternalSyncMetrics;
import io.mopl.domain.content.dto.ExternalContentSyncResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.storage.type=local",
    "spring.cloud.aws.region.static=ap-northeast-2",
    "mopl.content.thumbnail.storage-type=local",
    "spring.data.redis.password="
})
class ActuatorSecurityIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ContentExternalSyncMetrics contentExternalSyncMetrics;

  @Test
  void health_isPublic() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(jsonPath("$.status").exists());
  }

  @Test
  void metrics_rejectsNonAdminUser() throws Exception {
    mockMvc.perform(get("/actuator/metrics").with(user("user").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void prometheus_rejectsNonAdminUser() throws Exception {
    mockMvc.perform(get("/actuator/prometheus").with(user("user").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void admin_canReadContentMetricFromMetricsEndpoint() throws Exception {
    recordContentMetric();

    mockMvc.perform(
            get("/actuator/metrics/{metricName}", ContentExternalSyncMetrics.METRIC_NAME)
                .with(user("admin").roles("ADMIN"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(ContentExternalSyncMetrics.METRIC_NAME));
  }

  @Test
  void admin_canReadContentMetricFromPrometheusEndpoint() throws Exception {
    recordContentMetric();

    mockMvc.perform(get("/actuator/prometheus").with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("mopl_content_external_sync_items_total")));
  }

  private void recordContentMetric() {
    contentExternalSyncMetrics.record(new ExternalContentSyncResult(2, 2, 0, 1, 1, 0, null));
  }
}
