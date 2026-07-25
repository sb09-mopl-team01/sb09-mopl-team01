package io.mopl.domain.watchingsession.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WatchingSessionNodeIdTest {

  @Test
  void prefersConfiguredInstanceIdOverHostname() {
    assertThat(new WatchingSessionNodeId("instance-a", "host-a").value()).isEqualTo("instance-a");
  }

  @Test
  void usesHostnameWhenInstanceIdIsBlank() {
    assertThat(new WatchingSessionNodeId(" ", "host-a").value()).isEqualTo("host-a");
  }

  @Test
  void generatesIdWhenNoRuntimeIdentifierIsAvailable() {
    assertThat(new WatchingSessionNodeId("", "").value()).isNotBlank();
  }
}
