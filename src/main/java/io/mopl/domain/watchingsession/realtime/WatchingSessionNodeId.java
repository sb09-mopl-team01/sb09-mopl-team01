package io.mopl.domain.watchingsession.realtime;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WatchingSessionNodeId {

  private final String value;

  public WatchingSessionNodeId(
      @Value("${MOPL_INSTANCE_ID:}") String configuredNodeId,
      @Value("${HOSTNAME:}") String hostname
  ) {
    if (StringUtils.hasText(configuredNodeId)) {
      this.value = configuredNodeId;
    } else if (StringUtils.hasText(hostname)) {
      this.value = hostname;
    } else {
      this.value = UUID.randomUUID().toString();
    }
  }

  public String value() {
    return value;
  }
}
