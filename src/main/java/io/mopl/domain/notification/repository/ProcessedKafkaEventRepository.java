package io.mopl.domain.notification.repository;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedKafkaEventRepository {

  private static final String INSERT_IF_ABSENT = """
      INSERT INTO processed_kafka_events (id, event_key, processed_at)
      VALUES (?, ?, ?)
      ON CONFLICT (event_key) DO NOTHING
      """;
  private static final String H2_INSERT_IF_ABSENT = """
      INSERT INTO processed_kafka_events (id, event_key, processed_at)
      SELECT ?, ?, ?
      WHERE NOT EXISTS (
          SELECT 1 FROM processed_kafka_events WHERE event_key = ?
      )
      """;

  private final JdbcTemplate jdbcTemplate;
  private final boolean h2;

  public ProcessedKafkaEventRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
    this.jdbcTemplate = jdbcTemplate;
    this.h2 = isH2(dataSource);
  }

  /**
   * PostgreSQL의 ON CONFLICT로 동시에 같은 이벤트가 도착해도 한 트랜잭션만 처리 권한을 얻습니다.
   */
  public boolean registerIfAbsent(String eventKey, Instant processedAt) {
    UUID id = UUID.randomUUID();
    Timestamp jdbcProcessedAt = Timestamp.from(processedAt);
    if (h2) {
      return jdbcTemplate.update(H2_INSERT_IF_ABSENT, id, eventKey, jdbcProcessedAt, eventKey) == 1;
    }
    return jdbcTemplate.update(INSERT_IF_ABSENT, id, eventKey, jdbcProcessedAt) == 1;
  }

  private boolean isH2(DataSource dataSource) {
    try (var connection = dataSource.getConnection()) {
      return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    } catch (SQLException e) {
      throw new IllegalStateException("멱등성 저장소 데이터베이스 종류를 확인할 수 없습니다.", e);
    }
  }
}
