package io.mopl.domain.content.service;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentExternalSyncService {

  private final List<ExternalContentClient> externalContentClients;
  private final ContentRepository contentRepository;
  private final Clock clock;
  private final PlatformTransactionManager transactionManager;

  public ExternalContentSyncResult syncExternalContents() {
    Instant syncedAt = Instant.now(clock);
    int createdCount = 0;
    int skippedCount = 0;
    int failedCount = 0;

    List<ExternalContentCandidate> candidates = fetchAllCandidates();
    for (ExternalContentCandidate candidate : candidates) {
      try {
        if (syncCandidate(candidate, syncedAt)) {
          createdCount++;
        } else {
          skippedCount++;
        }
      } catch (IllegalArgumentException e) {
        failedCount++;
        log.warn(
            "Content external item skipped. source={}, externalId={}, reason={}",
            candidate == null ? null : candidate.source(),
            candidate == null ? null : candidate.externalId(),
            e.getMessage()
        );
      }
    }

    log.info(
        "Content external sync completed. createdCount={}, skippedCount={}, failedCount={}, syncedAt={}",
        createdCount,
        skippedCount,
        failedCount,
        syncedAt
    );
    return new ExternalContentSyncResult(createdCount, skippedCount, failedCount, syncedAt);
  }

  private List<ExternalContentCandidate> fetchAllCandidates() {
    List<ExternalContentCandidate> candidates = new ArrayList<>();
    for (ExternalContentClient client : externalContentClients) {
      candidates.addAll(fetchCandidates(client));
    }
    return candidates;
  }

  private List<ExternalContentCandidate> fetchCandidates(ExternalContentClient client) {
    try {
      List<ExternalContentCandidate> candidates = client.fetchContents();
      return candidates == null ? List.of() : candidates;
    } catch (ExternalApiException e) {
      log.error("Content external fetch failed. client={}", client.getClass().getSimpleName(), e);
      throw e;
    }
  }

  private boolean syncCandidate(ExternalContentCandidate candidate, Instant syncedAt) {
    if (candidate == null) {
      throw new IllegalArgumentException("외부 콘텐츠 후보가 비어 있습니다.");
    }
    validateCandidateIdentity(candidate);

    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return Boolean.TRUE.equals(transactionTemplate.execute(status -> syncCandidateInTransaction(candidate, syncedAt)));
  }

  private boolean syncCandidateInTransaction(ExternalContentCandidate candidate, Instant syncedAt) {
    return contentRepository.findBySourceAndExternalId(candidate.source(), candidate.externalId())
        .map(existingContent -> {
          existingContent.markSyncedAt(syncedAt);
          return false;
        })
        .orElseGet(() -> {
          Content content = Content.createExternal(
              candidate.type(),
              candidate.title(),
              candidate.description(),
              candidate.thumbnailUrl(),
              candidate.source(),
              candidate.externalId(),
              syncedAt,
              candidate.tags()
          );
          Content savedContent = contentRepository.save(content);
          log.info(
              "Content external item created. contentId={}, source={}, externalId={}",
              savedContent.getId(),
              savedContent.getSource(),
              savedContent.getExternalId()
          );
          return true;
        });
  }

  private void validateCandidateIdentity(ExternalContentCandidate candidate) {
    Objects.requireNonNull(candidate.type(), "외부 콘텐츠 타입이 비어 있습니다.");
    ContentSource source = Objects.requireNonNull(candidate.source(), "외부 콘텐츠 출처가 비어 있습니다.");
    if (!source.isExternal()) {
      throw new IllegalArgumentException("외부 콘텐츠는 MANUAL 출처를 사용할 수 없습니다.");
    }
    if (candidate.externalId() == null || candidate.externalId().isBlank()) {
      throw new IllegalArgumentException("외부 콘텐츠 externalId가 비어 있습니다.");
    }
  }
}
