package io.mopl.domain.content.service;

import io.mopl.domain.content.dto.ContentDto;
import io.mopl.domain.content.dto.ContentStats;
import io.mopl.domain.content.dto.request.ContentCreateRequest;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.mapper.ContentMapper;
import io.mopl.domain.content.repository.ContentRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ContentService {

  private final ContentRepository contentRepository;
  private final ContentStatsService contentStatsService;
  private final ContentMapper contentMapper;
  private final ContentThumbnailService contentThumbnailService;

  @Transactional
  public ContentDto createContent(ContentCreateRequest request, MultipartFile thumbnail) {
    String thumbnailUrl = null;
    try {
      thumbnailUrl = contentThumbnailService.uploadRequired(thumbnail);
      Content content = contentMapper.toEntity(request, thumbnailUrl);
      Content savedContent = contentRepository.save(content);
      log.info("Content create completed. contentId={}", savedContent.getId());
      return contentMapper.toDto(savedContent, contentStatsService.getStats(savedContent));
    } catch (IllegalArgumentException e) {
      contentThumbnailService.delete(thumbnailUrl);
      log.warn("Content create rejected. title={}", request == null ? null : request.title());
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  public ContentDto findContent(UUID contentId) {
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> {
          log.warn("Content find failed. contentId={}", contentId);
          return new BaseException(ErrorCode.INVALID_INPUT);
        });
    ContentStats stats = contentStatsService.getStats(content);

    return contentMapper.toDto(content, stats);
  }

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
}
