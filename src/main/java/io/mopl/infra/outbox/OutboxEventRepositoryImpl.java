package io.mopl.infra.outbox;

import static io.mopl.infra.outbox.QOutboxEvent.outboxEvent;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepositoryCustom {

  private final JPAQueryFactory queryFactory;
  private final EntityManager entityManager;

  @Override
  public List<UUID> findClaimableIds(Instant now, int batchSize) {
    List<?> eventIds = entityManager.createNativeQuery("""
        SELECT id
        FROM event_outbox
        WHERE status = :status
          AND next_attempt_at <= :now
        ORDER BY created_at ASC, id ASC
        FOR UPDATE SKIP LOCKED
        """)
        .setParameter("status", OutboxStatus.PENDING.name())
        .setParameter("now", now)
        .setMaxResults(batchSize)
        .getResultList();
    return eventIds.stream().map(this::toUuid).toList();
  }

  @Override
  public int recoverExpiredClaims(Instant expiredBefore, Instant now) {
    long affected = queryFactory
        .update(outboxEvent)
        .set(outboxEvent.status, OutboxStatus.PENDING)
        .setNull(outboxEvent.claimedAt)
        .set(outboxEvent.nextAttemptAt, now)
        .set(outboxEvent.updatedAt, now)
        .where(
            outboxEvent.status.eq(OutboxStatus.CLAIMED),
            outboxEvent.claimedAt.lt(expiredBefore)
        )
        .execute();
    clearPersistenceContext();
    return Math.toIntExact(affected);
  }

  @Override
  public int deletePublishedBefore(Instant publishedBefore) {
    long affected = queryFactory
        .delete(outboxEvent)
        .where(
            outboxEvent.status.eq(OutboxStatus.PUBLISHED),
            outboxEvent.publishedAt.lt(publishedBefore)
        )
        .execute();
    clearPersistenceContext();
    return Math.toIntExact(affected);
  }

  private void clearPersistenceContext() {
    entityManager.flush();
    entityManager.clear();
  }

  private UUID toUuid(Object value) {
    if (value instanceof UUID eventId) {
      return eventId;
    }
    if (value instanceof byte[] bytes && bytes.length == 16) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      return new UUID(buffer.getLong(), buffer.getLong());
    }
    return UUID.fromString(value.toString());
  }
}
