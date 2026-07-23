package io.mopl.domain.content.service;

import io.mopl.domain.content.dto.ContentThumbnailCleanupCandidate;
import io.mopl.domain.content.dto.ContentThumbnailCleanupResult;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.repository.ContentRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentThumbnailCleanupService {

  private final ContentRepository contentRepository;
  private final ContentThumbnailService contentThumbnailService;
  private final PlatformTransactionManager transactionManager;

  public ContentThumbnailCleanupResult cleanupExpiredThumbnails(Instant cutoff, int chunkSize) {
    Objects.requireNonNull(cutoff, "썸네일 정리 기준 시각은 필수입니다.");
    if (chunkSize <= 0) {
      throw new IllegalArgumentException("썸네일 정리 청크 크기는 1 이상이어야 합니다.");
    }

    Page<ContentThumbnailCleanupCandidate> firstPage = loadPage(cutoff, 0, chunkSize);
    int discoveredCount = 0;
    int cleanedCount = 0;
    int skippedCount = 0;
    int failedCount = 0;

    for (int pageNumber = firstPage.getTotalPages() - 1; pageNumber >= 0; pageNumber--) {
      Page<ContentThumbnailCleanupCandidate> candidatePage = pageNumber == 0
          ? firstPage
          : loadPage(cutoff, pageNumber, chunkSize);
      discoveredCount += candidatePage.getNumberOfElements();
      for (ContentThumbnailCleanupCandidate candidate : candidatePage.getContent()) {
        try {
          contentThumbnailService.deleteOrThrow(candidate.thumbnailKey());
          if (clearThumbnailMetadata(candidate, cutoff)) {
            cleanedCount++;
          } else {
            skippedCount++;
          }
        } catch (RuntimeException e) {
          failedCount++;
          log.warn(
              "Content thumbnailCleanup failed. contentId={}, thumbnailKey={}, errorType={}, message={}",
              candidate.contentId(),
              candidate.thumbnailKey(),
              e.getClass().getSimpleName(),
              e.getMessage(),
              e
          );
        }
      }
    }

    return new ContentThumbnailCleanupResult(
        discoveredCount,
        cleanedCount,
        skippedCount,
        failedCount
    );
  }

  private Page<ContentThumbnailCleanupCandidate> loadPage(
      Instant cutoff,
      int pageNumber,
      int chunkSize
  ) {
    return executeReadOnly(
        () -> contentRepository.findThumbnailCleanupCandidates(
            cutoff,
            PageRequest.of(pageNumber, chunkSize)
        )
    );
  }

  private boolean clearThumbnailMetadata(
      ContentThumbnailCleanupCandidate candidate,
      Instant cutoff
  ) {
    return Boolean.TRUE.equals(executeInTransaction(() -> {
      Content content = contentRepository.findByIdIncludingDeleted(candidate.contentId())
          .orElse(null);
      if (content == null
          || !content.isDeleted()
          || content.getDeletedAt().isAfter(cutoff)
          || !Objects.equals(content.getThumbnailKey(), candidate.thumbnailKey())) {
        return false;
      }
      content.clearThumbnailAfterRetention();
      return true;
    }));
  }

  private <T> T executeReadOnly(Supplier<T> action) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setReadOnly(true);
    return transactionTemplate.execute(status -> action.get());
  }

  private <T> T executeInTransaction(Supplier<T> action) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return transactionTemplate.execute(status -> action.get());
  }
}
