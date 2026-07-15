package io.mopl.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processed_kafka_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedKafkaEvent {

  @Id
  private UUID id;

  @Column(name = "event_key", nullable = false, unique = true, length = 255)
  private String eventKey;

  @Column(nullable = false, updatable = false)
  private Instant processedAt;
}
