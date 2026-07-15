package io.mopl.infra.outbox;

import static io.mopl.infra.outbox.QOutboxEvent.outboxEvent;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    return queryFactory
        .select(outboxEvent.id)
        .from(outboxEvent)
        .where(
            outboxEvent.status.eq(OutboxStatus.PENDING),
            outboxEvent.nextAttemptAt.loe(now)
        )
        .orderBy(outboxEvent.createdAt.asc(), outboxEvent.id.asc())
        .limit(batchSize)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .fetch();
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
}
