package io.mopl.infra.outbox;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.global.event.IntegrationEvent;
import io.mopl.infra.kafka.KafkaIntegrationEventProducer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock private OutboxEventClaimService outboxEventClaimService;
  @Mock private OutboxEventStateService outboxEventStateService;
  @Mock private KafkaIntegrationEventProducer kafkaIntegrationEventProducer;

  @Test
  @DisplayName("Kafka broker ACK를 받으면 Outbox 이벤트를 PUBLISHED로 처리한다")
  void relayMarksPublishedAfterKafkaAcknowledgement() {
    OutboxEvent event = claimedEvent();
    given(outboxEventClaimService.claimAvailableEvents()).willReturn(List.of(event));
    OutboxRelay relay = new OutboxRelay(
        outboxEventClaimService,
        outboxEventStateService,
        kafkaIntegrationEventProducer
    );

    relay.relay();

    verify(outboxEventStateService).recoverExpiredClaims();
    verify(kafkaIntegrationEventProducer).publish(event);
    verify(outboxEventStateService).markPublished(event.getId());
    verify(outboxEventStateService, never()).markPublishFailed(eq(event.getId()), any());
  }

  @Test
  @DisplayName("Kafka 발행에 실패하면 PUBLISHED 처리 대신 재시도 상태로 전이한다")
  void relaySchedulesRetryWhenKafkaPublishFails() {
    OutboxEvent event = claimedEvent();
    IllegalStateException exception = new IllegalStateException("broker unavailable");
    given(outboxEventClaimService.claimAvailableEvents()).willReturn(List.of(event));
    org.mockito.Mockito.doThrow(exception).when(kafkaIntegrationEventProducer).publish(event);
    OutboxRelay relay = new OutboxRelay(
        outboxEventClaimService,
        outboxEventStateService,
        kafkaIntegrationEventProducer
    );

    relay.relay();

    verify(outboxEventStateService).recoverExpiredClaims();
    verify(kafkaIntegrationEventProducer).publish(event);
    verify(outboxEventStateService).markPublishFailed(event.getId(), exception);
    verify(outboxEventStateService, never()).markPublished(event.getId());
    verifyNoMoreInteractions(kafkaIntegrationEventProducer);
  }

  private OutboxEvent claimedEvent() {
    Instant now = Instant.parse("2026-07-13T12:00:00Z");
    IntegrationEvent integrationEvent = new IntegrationEvent(
        "mopl.user.events",
        UUID.randomUUID().toString(),
        "user.created",
        1,
        "User",
        UUID.randomUUID(),
        OBJECT_MAPPER.createObjectNode().put("email", "mopl@example.com")
    );
    OutboxEvent event = OutboxEvent.create(integrationEvent, "{\"email\":\"mopl@example.com\"}", now);
    event.claim(now);
    return event;
  }
}
