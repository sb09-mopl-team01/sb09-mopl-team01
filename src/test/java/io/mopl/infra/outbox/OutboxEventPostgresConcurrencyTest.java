package io.mopl.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.global.event.IntegrationEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@Import({
    io.mopl.global.config.AppConfig.class,
    io.mopl.global.config.QueryDslConfig.class
})
@ActiveProfiles("test")
class OutboxEventPostgresConcurrencyTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Container
  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("spring.jpa.properties.hibernate.dialect",
        () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("다른 Relay가 잠근 이벤트는 건너뛰고 다음 이벤트를 선점 후보로 반환한다")
  void skipsRowsLockedByAnotherRelay() throws Exception {
    Instant now = Instant.parse("2026-07-20T00:00:00Z");
    UUID firstEventId = saveCommittedEvent(now.minusSeconds(2));
    UUID secondEventId = saveCommittedEvent(now.minusSeconds(1));
    CountDownLatch firstLockAcquired = new CountDownLatch(1);
    CountDownLatch releaseFirstRelay = new CountDownLatch(1);

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    try {
      Future<List<UUID>> firstRelay = executorService.submit(() -> inNewTransaction(() -> {
        List<UUID> claimedIds = outboxEventRepository.findClaimableIds(now, 1);
        firstLockAcquired.countDown();
        await(releaseFirstRelay);
        return claimedIds;
      }));

      assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

      Future<List<UUID>> secondRelay = executorService.submit(
          () -> inNewTransaction(() -> outboxEventRepository.findClaimableIds(now, 1))
      );

      assertThat(secondRelay.get(2, TimeUnit.SECONDS)).containsExactly(secondEventId);
      releaseFirstRelay.countDown();
      assertThat(firstRelay.get(5, TimeUnit.SECONDS)).containsExactly(firstEventId);
    } finally {
      releaseFirstRelay.countDown();
      executorService.shutdownNow();
    }
  }

  private UUID saveCommittedEvent(Instant createdAt) {
    return inNewTransaction(() -> {
      IntegrationEvent integrationEvent = new IntegrationEvent(
          "mopl.user.events",
          UUID.randomUUID().toString(),
          "user.created",
          1,
          "User",
          UUID.randomUUID(),
          OBJECT_MAPPER.createObjectNode().put("email", "mopl@example.com")
      );
      OutboxEvent event = OutboxEvent.create(
          integrationEvent, "{\"email\":\"mopl@example.com\"}", createdAt);
      return outboxEventRepository.saveAndFlush(event).getId();
    });
  }

  private <T> T inNewTransaction(Supplier<T> action) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return transactionTemplate.execute(status -> action.get());
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("첫 번째 Relay의 트랜잭션이 제한 시간 안에 완료되지 않았습니다.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Relay 선점 테스트가 중단되었습니다.", e);
    }
  }
}
