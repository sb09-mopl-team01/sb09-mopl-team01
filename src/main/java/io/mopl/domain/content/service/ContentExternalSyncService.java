package io.mopl.domain.content.service;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import io.mopl.infra.external.ExternalContentFetchResult;
import io.mopl.infra.external.InvalidExternalContentCandidateException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
    ExternalContentFetchResult fetchResult = fetchAllCandidates();
    int failedCount = fetchResult.failedCount();

    for (ExternalContentCandidate candidate : fetchResult.candidates()) {
      try {
        if (syncCandidate(candidate, syncedAt)) {
          createdCount++;
        } else {
          skippedCount++;
        }
      } catch (InvalidExternalContentCandidateException e) {
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
        "Content external sync completed. fetchedCount={}, acceptedCount={}, filteredCount={}, createdCount={}, skippedCount={}, failedCount={}, syncedAt={}",
        fetchResult.fetchedCount(),
        fetchResult.acceptedCount(),
        fetchResult.filteredCount(),
        createdCount,
        skippedCount,
        failedCount,
        syncedAt
    );
    return new ExternalContentSyncResult(
        fetchResult.fetchedCount(),
        fetchResult.acceptedCount(),
        fetchResult.filteredCount(),
        createdCount,
        skippedCount,
        failedCount,
        syncedAt
    );
  }

  private ExternalContentFetchResult fetchAllCandidates() {
    ExternalContentFetchResult result = ExternalContentFetchResult.empty();
    for (ExternalContentClient client : externalContentClients) {
      result = result.merge(fetchCandidates(client));
    }
    return result;
  }

  private ExternalContentFetchResult fetchCandidates(ExternalContentClient client) {
    String clientName = client.getClass().getSimpleName();
    try {
      ExternalContentFetchResult result = client.fetchContents();
      if (result == null) {
        throw new IllegalStateException("외부 콘텐츠 클라이언트가 수집 결과를 반환하지 않았습니다.");
      }
      log.info(
          "Content external fetch completed. client={}, fetchedCount={}, acceptedCount={}, filteredCount={}, failedCount={}",
          clientName,
          result.fetchedCount(),
          result.acceptedCount(),
          result.filteredCount(),
          result.failedCount()
      );
      return result;
    } catch (ExternalApiException e) {
      log.error("Content external fetch failed. client={}", clientName, e);
      throw e;
    }
  }

  private boolean syncCandidate(ExternalContentCandidate candidate, Instant syncedAt) {
    if (candidate == null) {
      throw invalid("외부 콘텐츠 후보가 비어 있습니다.");
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
    if (candidate.type() == null) {
      throw invalid("외부 콘텐츠 타입이 비어 있습니다.");
    }
    ContentSource source = candidate.source();
    if (source == null) {
      throw invalid("외부 콘텐츠 출처가 비어 있습니다.");
    }
    if (!source.isExternal()) {
      throw invalid("외부 콘텐츠는 MANUAL 출처를 사용할 수 없습니다.");
    }
    requireText(candidate.externalId(), 100, "외부 콘텐츠 externalId");
    requireText(candidate.title(), 255, "외부 콘텐츠 제목");
    requireText(candidate.description(), 2000, "외부 콘텐츠 설명");
    validateOptionalText(candidate.thumbnailUrl(), 2048, "외부 콘텐츠 썸네일 URL");
    validateTags(candidate.tags());
  }

  private void validateTags(Collection<String> tags) {
    if (tags == null || tags.isEmpty()) {
      throw invalid("외부 콘텐츠 태그는 하나 이상 필요합니다.");
    }
    for (String tag : tags) {
      requireText(tag, 50, "외부 콘텐츠 태그");
    }
  }

  private void requireText(String value, int maxLength, String fieldName) {
    if (value == null || value.isBlank()) {
      throw invalid(fieldName + "가 비어 있습니다.");
    }
    if (value.trim().length() > maxLength) {
      throw invalid(fieldName + "가 허용 길이를 초과했습니다.");
    }
  }

  private void validateOptionalText(String value, int maxLength, String fieldName) {
    if (value != null && !value.isBlank() && value.trim().length() > maxLength) {
      throw invalid(fieldName + "가 허용 길이를 초과했습니다.");
    }
  }

  private InvalidExternalContentCandidateException invalid(String message) {
    return new InvalidExternalContentCandidateException(message);
  }
}
