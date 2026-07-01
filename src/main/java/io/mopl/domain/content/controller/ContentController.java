package io.mopl.domain.content.controller;

import io.mopl.domain.content.dto.ContentDto;
import io.mopl.domain.content.dto.request.ContentCreateRequest;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.service.ContentService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
@Slf4j
public class ContentController {

  private static final List<String> SUPPORTED_SORT_BY = List.of("createdAt", "rate", "watcherCount");

  private final ContentService contentService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ContentDto> createContent(
      @Valid @RequestPart("request") ContentCreateRequest request,
      @RequestPart("thumbnail") MultipartFile thumbnail
  ) {
    ContentDto response = contentService.createContent(request, thumbnail);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{contentId}")
  public ResponseEntity<ContentDto> findContent(@PathVariable UUID contentId) {
    ContentDto response = contentService.findContent(contentId);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<CursorResponse<ContentDto>> findContents(
      @RequestParam(required = false) String typeEqual,
      @RequestParam(required = false) String keywordLike,
      @RequestParam(required = false) Collection<String> tagsIn,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      @RequestParam String sortBy,
      @RequestParam SortDirection sortDirection
  ) {
    validatePageParams(limit, sortBy);
    CursorResponse<ContentDto> response = contentService.findContents(
        parseType(typeEqual),
        keywordLike,
        tagsIn,
        cursor,
        idAfter,
        limit,
        sortBy,
        sortDirection
    );
    return ResponseEntity.ok(response);
  }

  private ContentType parseType(String typeEqual) {
    if (typeEqual == null || typeEqual.isBlank()) {
      return null;
    }

    try {
      return ContentType.from(typeEqual);
    } catch (IllegalArgumentException e) {
      log.warn("Content query rejected. typeEqual={}", typeEqual);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private void validatePageParams(int limit, String sortBy) {
    if (limit <= 0 || !SUPPORTED_SORT_BY.contains(sortBy)) {
      log.warn("Content query rejected. limit={}, sortBy={}", limit, sortBy);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }
}
