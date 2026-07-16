package io.mopl.domain.content.cache;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ContentCachePropertiesTest {

  @Test
  void rejectsNonPositiveTtl() {
    assertThatThrownBy(() -> new ContentCacheProperties(Duration.ZERO, Duration.ofMinutes(3)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ContentCacheProperties(Duration.ofMinutes(20), Duration.ofMinutes(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
