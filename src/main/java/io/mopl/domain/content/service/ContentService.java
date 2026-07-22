package io.mopl.domain.content.service;

import io.mopl.domain.content.cache.ContentCacheMapper;
import io.mopl.domain.content.cache.ContentCacheService;
import io.mopl.domain.content.cache.ContentCacheSnapshot;
import io.mopl.domain.content.dto.ContentDto;
import io.mopl.domain.content.dto.ContentStats;
import io.mopl.domain.content.dto.request.ContentCreateRequest;
import io.mopl.domain.content.dto.request.ContentUpdateRequest;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.event.ContentSoftDeletedEvent;
import io.mopl.domain.content.mapper.ContentMapper;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.storage.ContentThumbnailFile;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentService {

  private final ContentRepository contentRepository;
  private final ContentCacheService contentCacheService;
  private final ContentCacheMapper contentCacheMapper;
  private final ContentStatsService contentStatsService;
  private final ContentMapper contentMapper;
  private final ContentThumbnailService contentThumbnailService;
  private final DomainEventPublisher eventPublisher;
  private final PlatformTransactionManager transactionManager;
  private final Clock clock;

  public ContentDto createContent(ContentCreateRequest request, MultipartFile thumbnail) {
    ContentThumbnailFile uploadedThumbnail = null;
    try {
      uploadedThumbnail = contentThumbnailService.uploadRequired(thumbnail);
      ContentThumbnailFile thumbnailFile = uploadedThumbnail;

      return executeInTransaction(() -> {
        Content content = contentMapper.toEntity(request, thumbnailFile);
        Content savedContent = contentRepository.save(content);
        log.info("Content create completed. contentId={}", savedContent.getId());
        return contentMapper.toDto(savedContent, contentStatsService.getStats(savedContent));
      });
    } catch (IllegalArgumentException e) {
      deleteThumbnail(uploadedThumbnail);
      log.warn("Content create rejected. title={}", request == null ? null : request.title());
      throw new BaseException(ErrorCode.INVALID_INPUT);
    } catch (RuntimeException e) {
      deleteThumbnail(uploadedThumbnail);
      log.error("Content create failed. title={}", request == null ? null : request.title(), e);
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public ContentDto findContent(UUID contentId) {
    ContentCacheSnapshot snapshot = contentCacheService.find(contentId);
    if (!snapshot.isComplete()) {
      Content content = getContentOrThrow(contentId);
      snapshot = contentCacheService.resolveMissing(content, snapshot);
    }
    long watcherCount = contentStatsService.getWatcherCount(contentId);
    return contentCacheMapper.toDto(snapshot, watcherCount);
  }

  @Transactional(readOnly = true)
  public CursorResponse<ContentDto> findContents(
      ContentType typeEqual,
      String keywordLike,
      Collection<String> tagsIn,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      SortDirection sortDirection
  ) {
    CursorResponse<UUID> contentIds = contentRepository.findContentIdsByCursor(
        typeEqual,
        keywordLike,
        tagsIn,
        cursor,
        idAfter,
        limit,
        sortBy,
        sortDirection
    );

    Map<UUID, ContentCacheSnapshot> cachedByContentId = contentCacheService.findAll(contentIds.data());
    List<UUID> missingContentIds = contentIds.data().stream()
        .filter(contentId -> !cachedByContentId.getOrDefault(contentId, ContentCacheSnapshot.empty()).isComplete())
        .toList();

    Map<UUID, ContentCacheSnapshot> resolvedByContentId = cachedByContentId;
    if (!missingContentIds.isEmpty()) {
      List<Content> missingContents = contentRepository.findAllByIdWithTags(missingContentIds);
      resolvedByContentId = contentCacheService.resolveMissing(missingContents, cachedByContentId);
    }

    Map<UUID, Long> watcherCountsByContentId = contentStatsService.getWatcherCounts(contentIds.data());
    Map<UUID, ContentCacheSnapshot> finalResolvedByContentId = resolvedByContentId;
    List<ContentDto> contentDtos = contentIds.data().stream()
        .map(contentId -> {
          ContentCacheSnapshot snapshot = finalResolvedByContentId.get(contentId);
          if (snapshot == null || !snapshot.isComplete()) {
            log.warn("Content list assemble skipped. contentId={}, reason=content_missing", contentId);
            return null;
          }
          return contentCacheMapper.toDto(snapshot, watcherCountsByContentId.getOrDefault(contentId, 0L));
        })
        .filter(java.util.Objects::nonNull)
        .toList();

    return new CursorResponse<>(
        contentDtos,
        contentIds.nextCursor(),
        contentIds.nextIdAfter(),
        contentIds.hasNext(),
        contentIds.totalCount(),
        contentIds.sortBy(),
        contentIds.sortDirection()
    );
  }

  public ContentDto updateContent(
      UUID contentId,
      ContentUpdateRequest request,
      MultipartFile thumbnail
  ) {
    if (request == null) {
      log.warn("Content update rejected. contentId={}, reason=request_null", contentId);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    ContentThumbnailFile uploadedThumbnail = null;
    ContentUpdateOutcome outcome;

    try {
      uploadedThumbnail = contentThumbnailService.uploadOptional(thumbnail);
      ContentThumbnailFile replacementThumbnail = uploadedThumbnail;

      outcome = executeInTransaction(() -> {
        Content targetContent = getContentOrThrow(contentId);
        String previousThumbnailKey = targetContent.getThumbnailKey();
        String thumbnailUrl = replacementThumbnail == null
            ? targetContent.getThumbnailUrl()
            : replacementThumbnail.url();
        String thumbnailKey = replacementThumbnail == null
            ? previousThumbnailKey
            : replacementThumbnail.key();
        targetContent.updateManual(
            request.title(),
            request.description(),
            request.tags(),
            thumbnailUrl,
            thumbnailKey
        );
        log.info("Content update completed. contentId={}", contentId);
        ContentDto contentDto = contentMapper.toDto(
            targetContent,
            contentStatsService.getStats(targetContent)
        );
        return new ContentUpdateOutcome(
            contentDto,
            previousThumbnailKey,
            replacementThumbnail != null
        );
      });
    } catch (IllegalArgumentException e) {
      deleteThumbnail(uploadedThumbnail);
      log.warn("Content update rejected. contentId={}", contentId);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    } catch (RuntimeException e) {
      deleteThumbnail(uploadedThumbnail);
      log.error("Content update failed. contentId={}", contentId, e);
      throw e;
    }

    contentCacheService.evictAll(contentId);
    if (outcome.thumbnailChanged()) {
      contentThumbnailService.delete(outcome.previousThumbnailKey());
    }
    return outcome.contentDto();
  }

  public void deleteContent(UUID contentId) {
    executeWithoutResultInTransaction(() -> {
      Content content = getContentOrThrow(contentId);
      content.softDelete(Instant.now(clock));
      eventPublisher.publish(new ContentSoftDeletedEvent(contentId));
    });
    contentCacheService.evictAll(contentId);
    log.info("Content delete completed. contentId={}", contentId);
  }

  private Content getContentOrThrow(UUID contentId) {
    return contentRepository.findById(contentId)
        .orElseThrow(() -> {
          log.warn("Content find failed. contentId={}", contentId);
          return new BaseException(ErrorCode.INVALID_INPUT);
        });
  }

  private void deleteThumbnail(ContentThumbnailFile thumbnailFile) {
    if (thumbnailFile != null) {
      contentThumbnailService.delete(thumbnailFile.key());
    }
  }

  private <T> T executeInTransaction(java.util.function.Supplier<T> action) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return transactionTemplate.execute(status -> action.get());
  }

  private void executeWithoutResultInTransaction(Runnable action) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.executeWithoutResult(status -> action.run());
  }

  private record ContentUpdateOutcome(
      ContentDto contentDto,
      String previousThumbnailKey,
      boolean thumbnailChanged
  ) {
  }
}
