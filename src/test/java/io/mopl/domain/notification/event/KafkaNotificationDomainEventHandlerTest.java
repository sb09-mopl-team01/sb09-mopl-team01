package io.mopl.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.follow.event.FollowCreatedEvent;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class KafkaNotificationDomainEventHandlerTest {

  @Test
  @DisplayName("Kafka 알림 원본 이벤트는 커밋 전에 Outbox 저장 경로를 실행한다")
  void handlesSourceEventBeforeCommit() throws NoSuchMethodException {
    Method method = KafkaNotificationDomainEventHandler.class
        .getMethod("handleFollowCreated", FollowCreatedEvent.class);

    TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
  }
}
