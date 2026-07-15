package io.mopl.infra.outbox;

import io.mopl.global.event.IntegrationEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "event_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

  private static final int ERROR_MAX_LENGTH = 1000;

  @Id
  private UUID id;

  @Column(nullable = false, length = 100)
  private String aggregateType;

  @Column(nullable = false)
  private UUID aggregateId;

  @Column(nullable = false, length = 150)
  private String eventType;

  @Column(nullable = false)
  private int eventVersion;

  @Column(nullable = false, length = 249)
  private String topic;

  @Column(name = "event_key", nullable = false, length = 255)
  private String eventKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status;

  @Column(nullable = false)
  private int retryCount;

  @Column(nullable = false)
  private Instant nextAttemptAt;

  private Instant claimedAt;

  private Instant publishedAt;

  @Column(length = ERROR_MAX_LENGTH)
  private String lastError;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  private OutboxEvent(IntegrationEvent event, String payload, Instant now) {
    this.id = UUID.randomUUID();
    this.aggregateType = event.aggregateType();
    this.aggregateId = event.aggregateId();
    this.eventType = event.eventType();
    this.eventVersion = event.eventVersion();
    this.topic = event.topic();
    this.eventKey = event.key();
    this.payload = payload;
    this.status = OutboxStatus.PENDING;
    this.retryCount = 0;
    this.nextAttemptAt = now;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static OutboxEvent create(IntegrationEvent event, String payload, Instant now) {
    Objects.requireNonNull(event, "event must not be null");
    if (payload == null || payload.isBlank()) {
      throw new IllegalArgumentException("payload는 비어 있을 수 없습니다.");
    }
    return new OutboxEvent(event, payload, Objects.requireNonNull(now, "now must not be null"));
  }

  public void claim(Instant now) {
    if (status != OutboxStatus.PENDING) {
      throw new IllegalStateException("PENDING 상태의 이벤트만 선점할 수 있습니다.");
    }
    this.status = OutboxStatus.CLAIMED;
    this.claimedAt = now;
    this.updatedAt = now;
  }

  public void markPublished(Instant now) {
    requireClaimed();
    this.status = OutboxStatus.PUBLISHED;
    this.publishedAt = now;
    this.claimedAt = null;
    this.lastError = null;
    this.updatedAt = now;
  }

  public void markPublishFailed(Instant nextAttemptAt, int maxAttempts, String error, Instant now) {
    requireClaimed();
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts는 1 이상이어야 합니다.");
    }

    this.retryCount++;
    this.claimedAt = null;
    this.lastError = truncate(error);
    this.updatedAt = now;

    if (retryCount >= maxAttempts) {
      this.status = OutboxStatus.FAILED;
      this.nextAttemptAt = now;
      return;
    }

    this.status = OutboxStatus.PENDING;
    this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
  }

  private void requireClaimed() {
    if (status != OutboxStatus.CLAIMED) {
      throw new IllegalStateException("CLAIMED 상태의 이벤트만 완료 처리할 수 있습니다.");
    }
  }

  private static String truncate(String error) {
    if (error == null || error.isBlank()) {
      return "Unknown Kafka publish failure";
    }
    return error.length() <= ERROR_MAX_LENGTH ? error : error.substring(0, ERROR_MAX_LENGTH);
  }
}
