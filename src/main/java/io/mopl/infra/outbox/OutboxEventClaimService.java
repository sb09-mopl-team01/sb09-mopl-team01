package io.mopl.infra.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxEventClaimService {

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxProperties outboxProperties;
  private final Clock clock;

  /**
   * PostgreSQL의 SKIP LOCKED로 다른 Relay가 이미 처리 중인 행은 건너뛴 뒤 선점합니다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<OutboxEvent> claimAvailableEvents() {
    Instant now = Instant.now(clock);
    List<UUID> eventIds = outboxEventRepository.findClaimableIds(now, outboxProperties.batchSize());
    if (eventIds.isEmpty()) {
      return List.of();
    }

    Map<UUID, OutboxEvent> eventsById = new HashMap<>();
    outboxEventRepository.findAllById(eventIds)
        .forEach(event -> eventsById.put(event.getId(), event));

    return eventIds.stream()
        .map(eventsById::get)
        .filter(event -> event != null && event.getStatus() == OutboxStatus.PENDING)
        .peek(event -> event.claim(now))
        .toList();
  }
}
