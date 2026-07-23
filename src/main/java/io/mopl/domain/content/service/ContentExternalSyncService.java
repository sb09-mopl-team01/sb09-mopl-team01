package io.mopl.domain.content.service;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import io.mopl.infra.external.ExternalContentFetchResult;
import io.mopl.infra.external.InvalidExternalContentCandidateException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentExternalSyncService {

  static final int SYNC_CHUNK_SIZE = 100;

  private final List<ExternalContentClient> externalContentClients;
  private final ContentRepository contentRepository;
  private final ContentSearchIndexService contentSearchIndexService;
  private final Clock clock;
  private final PlatformTransactionManager transactionManager;

  public ExternalContentSyncResult syncExternalContents() {
    Instant syncedAt = Instant.now(clock);
    ExternalContentFetchResult fetchResult = fetchAllCandidates();
    CandidatePreparationResult preparationResult = prepareCandidates(fetchResult.candidates());
    int createdCount = 0;
    int skippedCount = preparationResult.duplicateCount();

    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    List<ExternalContentCandidate> candidates = preparationResult.candidates();
    for (int start = 0; start < candidates.size(); start += SYNC_CHUNK_SIZE) {
      int end = Math.min(start + SYNC_CHUNK_SIZE, candidates.size());
      List<ExternalContentCandidate> chunk = candidates.subList(start, end);
      SyncChunkResult chunkResult = transactionTemplate.execute(
          status -> syncChunkInTransaction(chunk, syncedAt)
      );
      if (chunkResult == null) {
        throw new IllegalStateException("외부 콘텐츠 청크 동기화 결과가 비어 있습니다.");
      }
      contentSearchIndexService.indexAll(chunkResult.createdContentIds());
      createdCount += chunkResult.createdCount();
      skippedCount += chunkResult.skippedCount();
    }

    int failedCount = fetchResult.failedCount() + preparationResult.failedCount();

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

  private CandidatePreparationResult prepareCandidates(List<ExternalContentCandidate> candidates) {
    Map<ExternalContentKey, ExternalContentCandidate> uniqueCandidates = new LinkedHashMap<>();
    int duplicateCount = 0;
    int failedCount = 0;

    for (ExternalContentCandidate candidate : candidates) {
      try {
        validateCandidate(candidate);
        ExternalContentKey key = ExternalContentKey.from(candidate);
        if (uniqueCandidates.putIfAbsent(key, candidate) != null) {
          duplicateCount++;
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
    return new CandidatePreparationResult(
        List.copyOf(uniqueCandidates.values()),
        duplicateCount,
        failedCount
    );
  }

  private SyncChunkResult syncChunkInTransaction(
      List<ExternalContentCandidate> candidates,
      Instant syncedAt
  ) {
    Set<ContentSource> sources = new LinkedHashSet<>();
    Set<ContentType> types = new LinkedHashSet<>();
    Set<String> externalIds = new LinkedHashSet<>();
    for (ExternalContentCandidate candidate : candidates) {
      sources.add(candidate.source());
      types.add(candidate.type());
      externalIds.add(candidate.externalId());
    }

    Map<ExternalContentKey, Content> existingByKey = new LinkedHashMap<>();
    for (Content content : contentRepository.findAllBySourceInAndTypeInAndExternalIdIn(
        sources,
        types,
        externalIds
    )) {
      existingByKey.put(ExternalContentKey.from(content), content);
    }

    List<Content> newContents = new ArrayList<>();
    int skippedCount = 0;
    for (ExternalContentCandidate candidate : candidates) {
      Content existingContent = existingByKey.get(ExternalContentKey.from(candidate));
      if (existingContent != null) {
        if (!existingContent.isDeleted()) {
          existingContent.markSyncedAt(syncedAt);
        }
        skippedCount++;
        continue;
      }
      newContents.add(toContent(candidate, syncedAt));
    }

    List<Content> savedContents = contentRepository.saveAll(newContents);
    for (Content savedContent : savedContents) {
      log.debug(
          "Content external item created. contentId={}, source={}, externalId={}",
          savedContent.getId(),
          savedContent.getSource(),
          savedContent.getExternalId()
      );
    }
    return new SyncChunkResult(
        savedContents.size(),
        skippedCount,
        savedContents.stream().map(Content::getId).toList()
    );
  }

  private Content toContent(ExternalContentCandidate candidate, Instant syncedAt) {
    return Content.createExternal(
        candidate.type(),
        candidate.title(),
        candidate.description(),
        candidate.thumbnailUrl(),
        candidate.source(),
        candidate.externalId(),
        syncedAt,
        candidate.tags()
    );
  }

  private void validateCandidate(ExternalContentCandidate candidate) {
    if (candidate == null) {
      throw invalid("외부 콘텐츠 후보가 비어 있습니다.");
    }
    validateCandidateIdentity(candidate);
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

  private record CandidatePreparationResult(
      List<ExternalContentCandidate> candidates,
      int duplicateCount,
      int failedCount
  ) {
  }

  private record SyncChunkResult(
      int createdCount,
      int skippedCount,
      List<UUID> createdContentIds
  ) {
  }

  private record ExternalContentKey(ContentSource source, ContentType type, String externalId) {

    private static ExternalContentKey from(ExternalContentCandidate candidate) {
      return new ExternalContentKey(candidate.source(), candidate.type(), candidate.externalId());
    }

    private static ExternalContentKey from(Content content) {
      return new ExternalContentKey(content.getSource(), content.getType(), content.getExternalId());
    }
  }
}
