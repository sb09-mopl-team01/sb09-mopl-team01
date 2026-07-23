package io.mopl.domain.watchingsession.realtime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WatchingSessionLeaseProperties.class)
public class WatchingSessionLeaseConfig {
}
