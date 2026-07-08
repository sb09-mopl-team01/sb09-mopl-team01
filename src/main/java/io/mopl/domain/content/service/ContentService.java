package io.mopl.domain.content.service;

import io.mopl.domain.content.dto.ContentDto;
import io.mopl.domain.content.dto.ContentStats;
import io.mopl.domain.content.dto.request.ContentCreateRequest;
import io.mopl.domain.content.dto.request.ContentUpdateRequest;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.mapper.ContentMapper;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.storage.ContentThumbnailFile;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
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
  private final ContentStatsService contentStatsService;
  private final ContentMapper contentMapper;
  private final ContentThumbnailService contentThumbnailService;
  private final PlatformTransactionManager transactionManager;

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
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> {
          log.warn("Content find failed. contentId={}", contentId);
          return new BaseException(ErrorCode.INVALID_INPUT);
        });
    ContentStats stats = contentStatsService.getStats(content);

    return contentMapper.toDto(content, stats);
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
    CursorResponse<Content> contents = contentRepository.findContentsByCursor(
        typeEqual,
        keywordLike,
        tagsIn,
        cursor,
        idAfter,
        limit,
        sortBy,
        sortDirection
    );

    Map<UUID, ContentStats> statsByContentId = contentStatsService.getStatsByContents(contents.data());

    List<ContentDto> contentDtos = contents.data().stream()
        .map(content -> contentMapper.toDto(content, statsByContentId.get(content.getId())))
        .toList();

    return new CursorResponse<>(
        contentDtos,
        contents.nextCursor(),
        contents.nextIdAfter(),
        contents.hasNext(),
        contents.totalCount(),
        contents.sortBy(),
        contents.sortDirection()
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

    Content content = getContentOrThrow(contentId);
    String currentThumbnailUrl = content.getThumbnailUrl();
    String currentThumbnailKey = content.getThumbnailKey();
    ContentThumbnailFile uploadedThumbnail = null;

    try {
      uploadedThumbnail = contentThumbnailService.uploadOptional(thumbnail);
      String thumbnailUrl = uploadedThumbnail == null ? currentThumbnailUrl : uploadedThumbnail.url();
      String thumbnailKey = uploadedThumbnail == null ? currentThumbnailKey : uploadedThumbnail.key();
      boolean thumbnailChanged = uploadedThumbnail != null;

      ContentDto contentDto = executeInTransaction(() -> {
        Content targetContent = getContentOrThrow(contentId);
        targetContent.updateManual(
            request.title(),
            request.description(),
            request.tags(),
            thumbnailUrl,
            thumbnailKey
        );
        log.info("Content update completed. contentId={}", contentId);
        return contentMapper.toDto(targetContent, contentStatsService.getStats(targetContent));
      });

      if (thumbnailChanged) {
        contentThumbnailService.delete(currentThumbnailKey);
      }
      return contentDto;
    } catch (IllegalArgumentException e) {
      deleteThumbnail(uploadedThumbnail);
      log.warn("Content update rejected. contentId={}", contentId);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    } catch (RuntimeException e) {
      deleteThumbnail(uploadedThumbnail);
      log.error("Content update failed. contentId={}", contentId, e);
      throw e;
    }
  }

  public void deleteContent(UUID contentId) {
    String thumbnailKey = executeInTransaction(() -> {
      Content content = getContentOrThrow(contentId);
      String currentThumbnailKey = content.getThumbnailKey();
      contentRepository.delete(content);
      return currentThumbnailKey;
    });
    contentThumbnailService.delete(thumbnailKey);
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
}
