package io.mopl.global.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@Import(QueryDslConfig.class)
public class AppConfig {

  @Bean
  public DateTimeProvider auditingDateTimeProvider() {
    return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
  }
}
