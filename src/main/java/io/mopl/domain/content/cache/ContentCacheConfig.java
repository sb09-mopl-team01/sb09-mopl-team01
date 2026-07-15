package io.mopl.domain.content.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ContentCacheProperties.class)
public class ContentCacheConfig {
}
