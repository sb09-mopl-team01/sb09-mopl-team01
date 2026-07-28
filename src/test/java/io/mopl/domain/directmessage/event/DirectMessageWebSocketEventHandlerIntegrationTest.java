package io.mopl.domain.directmessage.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.realtime.DirectMessageRealtimeEvent;
import io.mopl.domain.directmessage.realtime.DirectMessageRealtimePublisher;
import io.mopl.domain.directmessage.service.ConversationService;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.event.SpringDomainEventPublisher;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(classes = DirectMessageWebSocketEventHandlerIntegrationTest.TestConfig.class)
class DirectMessageWebSocketEventHandlerIntegrationTest {

  @Autowired
  private DomainEventPublisher eventPublisher;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private ConversationService conversationService;

  @Autowired
  private DirectMessageRealtimePublisher realtimePublisher;

  @AfterEach
  void tearDown() {
    reset(conversationService, realtimePublisher);
  }

  @Test
  void broadcastsDirectMessageAfterTransactionCommit() throws InterruptedException {
    DirectMessageSentEvent event = createEvent();
    DirectMessageDto message = createDirectMessageDto(event);
    CountDownLatch latch = new CountDownLatch(1);
    when(conversationService.findDirectMessage(event.directMessageId())).thenReturn(message);
    doAnswer(invocation -> {
      latch.countDown();
      return null;
    }).when(realtimePublisher).publish(new DirectMessageRealtimeEvent(event.conversationId(), message));

    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
        eventPublisher.publish(event)
    );

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    verify(conversationService).findDirectMessage(event.directMessageId());
    verify(realtimePublisher).publish(new DirectMessageRealtimeEvent(event.conversationId(), message));
  }

  @Test
  void doesNotBroadcastDirectMessageWhenTransactionRollsBack() throws InterruptedException {
    DirectMessageSentEvent event = createEvent();
    DirectMessageDto message = createDirectMessageDto(event);
    CountDownLatch latch = new CountDownLatch(1);
    when(conversationService.findDirectMessage(event.directMessageId())).thenReturn(message);
    doAnswer(invocation -> {
      latch.countDown();
      return null;
    }).when(realtimePublisher).publish(new DirectMessageRealtimeEvent(event.conversationId(), message));

    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      eventPublisher.publish(event);
      status.setRollbackOnly();
    });

    assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
    verify(conversationService, never()).findDirectMessage(event.directMessageId());
    verify(realtimePublisher, never()).publish(new DirectMessageRealtimeEvent(event.conversationId(), message));
  }

  private DirectMessageSentEvent createEvent() {
    return new DirectMessageSentEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "sender",
        UUID.randomUUID(),
        Instant.parse("2026-07-23T01:00:00Z")
    );
  }

  private DirectMessageDto createDirectMessageDto(DirectMessageSentEvent event) {
    return new DirectMessageDto(
        event.directMessageId(),
        event.conversationId(),
        event.occurredAt(),
        UserSummary.builder()
            .userId(event.senderId())
            .name(event.senderName())
            .profileImageUrl(null)
            .build(),
        UserSummary.builder()
            .userId(event.receiverId())
            .name("receiver")
            .profileImageUrl(null)
            .build(),
        "hello"
    );
  }

  @Configuration
  @EnableAsync
  @EnableTransactionManagement
  static class TestConfig {

    @Bean
    DomainEventPublisher domainEventPublisher(ApplicationEventPublisher eventPublisher) {
      return new SpringDomainEventPublisher(eventPublisher);
    }

    @Bean
    DirectMessageWebSocketEventHandler directMessageWebSocketEventHandler(
        ConversationService conversationService,
        DirectMessageRealtimePublisher realtimePublisher
    ) {
      return new DirectMessageWebSocketEventHandler(conversationService, realtimePublisher);
    }

    @Bean(name = "directMessageRealtimeExecutor")
    Executor directMessageRealtimeExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(1);
      executor.setMaxPoolSize(1);
      executor.setQueueCapacity(10);
      executor.setThreadNamePrefix("dm-realtime-test-");
      executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
      executor.initialize();
      return executor;
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
      };
    }

    @Bean
    ConversationService conversationService() {
      return mock(ConversationService.class);
    }

    @Bean
    DirectMessageRealtimePublisher directMessageRealtimePublisher() {
      return mock(DirectMessageRealtimePublisher.class);
    }
  }
}
