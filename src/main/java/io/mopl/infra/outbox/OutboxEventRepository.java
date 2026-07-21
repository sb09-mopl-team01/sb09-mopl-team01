package io.mopl.infra.outbox;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends
    JpaRepository<OutboxEvent, UUID>,
    OutboxEventRepositoryCustom {

  Optional<OutboxEvent> findByDeduplicationKey(String deduplicationKey);
}
