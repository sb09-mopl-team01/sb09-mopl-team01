package io.mopl.infra.external;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@EnableConfigurationProperties(ExternalContentApiProperties.class)
public class ExternalContentClientConfig {

  @Bean
  public RestClientCustomizer externalContentRestClientCustomizer(ExternalContentApiProperties properties) {
    return restClientBuilder -> restClientBuilder.requestFactory(createRequestFactory(properties.timeout()));
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  private static SimpleClientHttpRequestFactory createRequestFactory(
      ExternalContentApiProperties.Timeout timeout
  ) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(Math.max(1, timeout.connectSeconds())));
    requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(1, timeout.readSeconds())));
    return requestFactory;
  }
}
