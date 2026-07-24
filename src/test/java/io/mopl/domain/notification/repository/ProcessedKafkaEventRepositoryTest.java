package io.mopl.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ProcessedKafkaEventRepositoryTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private DataSource dataSource;

  @Mock
  private Connection connection;

  @Mock
  private DatabaseMetaData databaseMetaData;

  private ProcessedKafkaEventRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(databaseMetaData);
    when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
    repository = new ProcessedKafkaEventRepository(jdbcTemplate, dataSource);
  }

  @Test
  @DisplayName("PostgreSQL 멱등성 등록 시 Instant를 JDBC Timestamp로 바인딩한다")
  void registerIfAbsentBindsProcessedAtAsTimestamp() {
    String eventKey = UUID.randomUUID().toString();
    Instant processedAt = Instant.parse("2026-07-24T04:00:00Z");
    when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

    boolean registered = repository.registerIfAbsent(eventKey, processedAt);

    ArgumentCaptor<Object> processedAtCaptor = ArgumentCaptor.forClass(Object.class);
    verify(jdbcTemplate).update(anyString(), any(UUID.class), eq(eventKey), processedAtCaptor.capture());
    assertThat(registered).isTrue();
    assertThat(processedAtCaptor.getValue()).isInstanceOf(Timestamp.class);
    assertThat(((Timestamp) processedAtCaptor.getValue()).toInstant()).isEqualTo(processedAt);
  }
}
