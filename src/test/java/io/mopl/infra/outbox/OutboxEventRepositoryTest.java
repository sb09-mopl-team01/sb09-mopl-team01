package io.mopl.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.follow.event.FollowCreatedEvent;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.notification.event.KafkaNotificationDomainEventHandler;
import io.mopl.domain.notification.event.KafkaNotificationRequestedEventHandler;
import io.mopl.domain.notification.event.NotificationMessageFactory;
import io.mopl.domain.notification.event.NotificationRequestedEvent;
import io.mopl.global.event.IntegrationEvent;
import io.mopl.infra.kafka.NotificationKafkaProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({
    io.mopl.global.config.AppConfig.class,
    io.mopl.global.config.QueryDslConfig.class
})
@ActiveProfiles("test")
class OutboxEventRepositoryTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("발행 시각이 지난 PENDING 이벤트만 Relay 선점 후보로 조회한다")
  void findClaimableIds() {
    Instant now = Instant.parse("2026-07-13T12:00:00Z");
    OutboxEvent ready = saveEvent(now.minusSeconds(1));
    OutboxEvent delayed = saveEvent(now.plusSeconds(1));

    List<UUID> claimableIds = outboxEventRepository.findClaimableIds(now, 10);

    assertThat(claimableIds).containsExactly(ready.getId());
    assertThat(claimableIds).doesNotContain(delayed.getId());
  }

  @Test
  @DisplayName("선점 시간이 만료된 이벤트를 재시도 가능한 PENDING 상태로 복구한다")
  void recoverExpiredClaims() {
    Instant claimedAt = Instant.parse("2026-07-13T12:00:00Z");
    OutboxEvent event = saveEvent(claimedAt.minusSeconds(1));
    event.claim(claimedAt);
    outboxEventRepository.saveAndFlush(event);

    int recovered = outboxEventRepository.recoverExpiredClaims(
        claimedAt.plusSeconds(30),
        claimedAt.plusSeconds(60)
    );

    OutboxEvent recoveredEvent = outboxEventRepository.findById(event.getId()).orElseThrow();
    assertThat(recovered).isEqualTo(1);
    assertThat(recoveredEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(recoveredEvent.getClaimedAt()).isNull();
    assertThat(recoveredEvent.getNextAttemptAt()).isEqualTo(claimedAt.plusSeconds(60));
  }

  @Test
  @DisplayName("보관 기간을 지난 PUBLISHED 이벤트만 정리한다")
  void deletePublishedBefore() {
    Instant publishedAt = Instant.parse("2026-07-13T12:00:00Z");
    OutboxEvent published = saveEvent(publishedAt.minusSeconds(1));
    published.claim(publishedAt);
    published.markPublished(publishedAt);
    outboxEventRepository.saveAndFlush(published);
    OutboxEvent pending = saveEvent(publishedAt.minusSeconds(1));

    int deleted = outboxEventRepository.deletePublishedBefore(publishedAt.plusSeconds(1));

    assertThat(deleted).isEqualTo(1);
    assertThat(outboxEventRepository.findById(published.getId())).isEmpty();
    assertThat(outboxEventRepository.findById(pending.getId())).isPresent();
  }

  @Test
  @DisplayName("같은 sourceEventId와 receiverId의 Kafka 알림 요청은 Outbox에 한 번만 저장한다")
  void savesDuplicateNotificationRequestOnlyOnce() {
    UUID sourceEventId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    KafkaNotificationRequestedEventHandler handler = kafkaHandler();

    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      NotificationRequestedEvent event = new NotificationRequestedEvent(
          sourceEventId, receiverId, "중복 방지", "한 번만 저장됩니다", NotificationLevel.INFO);
      handler.handle(event);
      handler.handle(event);
    });

    assertThat(outboxEventRepository.count()).isEqualTo(1);
    assertThat(outboxEventRepository.findAll().get(0).getDeduplicationKey())
        .isEqualTo(sourceEventId + ":" + receiverId);
  }

  @Test
  @DisplayName("원본 트랜잭션이 롤백되면 Kafka 알림 Outbox도 남지 않는다")
  void rollsBackOutboxWithSourceTransaction() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    KafkaNotificationDomainEventHandler handler = new KafkaNotificationDomainEventHandler(
        new NotificationMessageFactory(OBJECT_MAPPER), kafkaHandler());

    transactionTemplate.executeWithoutResult(status -> {
      handler.handleFollowCreated(new FollowCreatedEvent(
          UUID.randomUUID(), UUID.randomUUID(), "발신자", UUID.randomUUID(), Instant.now()));
      status.setRollbackOnly();
    });

    assertThat(outboxEventRepository.count()).isZero();
  }

  private KafkaNotificationRequestedEventHandler kafkaHandler() {
    return new KafkaNotificationRequestedEventHandler(
        new OutboxIntegrationEventPublisher(outboxEventRepository, OBJECT_MAPPER, Clock.systemUTC()),
        OBJECT_MAPPER,
        new NotificationKafkaProperties("notification", "notification-dlt", "test", 3, Duration.ofSeconds(1))
    );
  }

  private OutboxEvent saveEvent(Instant now) {
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
    return outboxEventRepository.saveAndFlush(event);
  }
}
