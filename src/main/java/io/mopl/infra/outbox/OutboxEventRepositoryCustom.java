package io.mopl.infra.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepositoryCustom {

  List<UUID> findClaimableIds(Instant now, int batchSize);

  int recoverExpiredClaims(Instant expiredBefore, Instant now);

  int deletePublishedBefore(Instant publishedBefore);
}
