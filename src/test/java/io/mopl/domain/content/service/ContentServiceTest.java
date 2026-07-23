package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.cache.ContentCacheMapper;
import io.mopl.domain.content.cache.ContentCacheService;
import io.mopl.domain.content.cache.ContentCacheSnapshot;
import io.mopl.domain.content.cache.dto.ContentBaseCache;
import io.mopl.domain.content.cache.dto.ContentStatsCache;
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
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-20T00:00:00Z");

  private ContentService contentService;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentCacheService contentCacheService;

  @Mock
  private ContentCacheMapper contentCacheMapper;

  @Mock
  private ContentStatsService contentStatsService;

  @Mock
  private ContentMapper contentMapper;

  @Mock
  private ContentThumbnailService contentThumbnailService;

  @Mock
  private DomainEventPublisher eventPublisher;
  private ContentSearchQueryService contentSearchQueryService;

  @Mock
  private ContentSearchIndexService contentSearchIndexService;

  @BeforeEach
  void setUp() {
    contentService = new ContentService(
        contentRepository,
        contentCacheService,
        contentCacheMapper,
        contentStatsService,
        contentMapper,
        contentThumbnailService,
        eventPublisher,
        contentSearchQueryService,
        contentSearchIndexService,
        new ResourcelessTransactionManager(),
        Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void findContent() {
    Content content = manualContent("movie", "description", "https://image.example.com/movie.jpg", Set.of("action"));
    UUID contentId = UUID.randomUUID();
    ReflectionTestUtils.setField(content, "id", contentId);
    content.updateReviewStats(4.5, 2);
    ContentDto expectedDto = dto(contentId, "movie", "description", "https://image.example.com/movie.jpg",
        Set.of("action"), 4.5, 2);
    ContentCacheSnapshot cached = ContentCacheSnapshot.empty();
    ContentCacheSnapshot resolved = snapshot(content);
    given(contentCacheService.find(contentId)).willReturn(cached);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
    given(contentCacheService.resolveMissing(content, cached)).willReturn(resolved);
    given(contentStatsService.getWatcherCount(contentId)).willReturn(0L);
    given(contentCacheMapper.toDto(resolved, 0L)).willReturn(expectedDto);

    ContentDto result = contentService.findContent(contentId);

    assertThat(result).isEqualTo(expectedDto);
  }

  @Test
  void rejectUnknownContent() {
    UUID contentId = UUID.randomUUID();
    given(contentCacheService.find(contentId)).willReturn(ContentCacheSnapshot.empty());
    given(contentRepository.findById(contentId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.findContent(contentId))
        .isInstanceOf(BaseException.class);
  }

  @Test
  void findContents() {
    Content content = manualContent("movie", "description", null, Set.of("action"));
    UUID contentId = UUID.randomUUID();
    ReflectionTestUtils.setField(content, "id", contentId);
    content.updateReviewStats(4.0, 3);
    ContentDto expectedDto = dto(contentId, "movie", "description", null, Set.of("action"), 4.0, 3);
    CursorResponse<UUID> repositoryResponse = new CursorResponse<>(
        List.of(contentId),
        "2026-06-29T00:00:00Z",
        contentId,
        true,
        1L,
        "createdAt",
        SortDirection.DESCENDING
    );
    given(contentRepository.findContentIdsByCursor(
        ContentType.MOVIE,
        "movie",
        List.of("action"),
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    )).willReturn(repositoryResponse);
    given(contentSearchQueryService.search(
        ContentType.MOVIE,
        "movie",
        List.of("action"),
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    )).willReturn(Optional.empty());
    Map<UUID, ContentCacheSnapshot> cached = Map.of(contentId, ContentCacheSnapshot.empty());
    Map<UUID, ContentCacheSnapshot> resolved = Map.of(contentId, snapshot(content));
    given(contentCacheService.findAll(List.of(contentId))).willReturn(cached);
    given(contentRepository.findAllByIdWithTags(List.of(contentId))).willReturn(List.of(content));
    given(contentCacheService.resolveMissing(List.of(content), cached)).willReturn(resolved);
    given(contentStatsService.getWatcherCounts(List.of(contentId))).willReturn(Map.of(contentId, 0L));
    given(contentCacheMapper.toDto(resolved.get(contentId), 0L)).willReturn(expectedDto);

    CursorResponse<ContentDto> result = contentService.findContents(
        ContentType.MOVIE,
        "movie",
        List.of("action"),
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(result.data()).containsExactly(expectedDto);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.totalCount()).isEqualTo(1L);
  }

  @Test
  void findContentFromCacheWithoutContentEntityLookup() {
    UUID contentId = UUID.randomUUID();
    Content content = manualContent("movie", "description", null, Set.of("action"));
    ReflectionTestUtils.setField(content, "id", contentId);
    ContentCacheSnapshot snapshot = snapshot(content);
    ContentDto expectedDto = dto(contentId, "movie", "description", null, Set.of("action"), 0.0, 0);
    given(contentCacheService.find(contentId)).willReturn(snapshot);
    given(contentStatsService.getWatcherCount(contentId)).willReturn(2L);
    given(contentCacheMapper.toDto(snapshot, 2L)).willReturn(expectedDto);

    ContentDto result = contentService.findContent(contentId);

    assertThat(result).isEqualTo(expectedDto);
    verify(contentRepository, never()).findById(contentId);
  }

  @Test
  void findContentsBulkLoadsOnlyCacheMissesAndKeepsDatabaseOrder() {
    UUID hitId = UUID.randomUUID();
    UUID missId = UUID.randomUUID();
    Content hitContent = manualContent("cached", "cached description", null, Set.of("cached-tag"));
    Content missContent = manualContent("miss", "miss description", null, Set.of("miss-tag"));
    ReflectionTestUtils.setField(hitContent, "id", hitId);
    ReflectionTestUtils.setField(missContent, "id", missId);
    ContentCacheSnapshot hitSnapshot = snapshot(hitContent);
    ContentCacheSnapshot missSnapshot = ContentCacheSnapshot.empty();
    ContentCacheSnapshot resolvedMiss = snapshot(missContent);
    ContentDto hitDto = dto(hitId, "cached", "cached description", null, Set.of("cached-tag"), 0.0, 0);
    ContentDto missDto = dto(missId, "miss", "miss description", null, Set.of("miss-tag"), 0.0, 0);
    CursorResponse<UUID> repositoryResponse = new CursorResponse<>(
        List.of(hitId, missId), null, missId, false, 2L, "createdAt", SortDirection.DESCENDING
    );
    given(contentRepository.findContentIdsByCursor(
        null, null, null, null, null, 10, "createdAt", SortDirection.DESCENDING
    )).willReturn(repositoryResponse);
    Map<UUID, ContentCacheSnapshot> cached = Map.of(hitId, hitSnapshot, missId, missSnapshot);
    Map<UUID, ContentCacheSnapshot> resolved = Map.of(hitId, hitSnapshot, missId, resolvedMiss);
    given(contentCacheService.findAll(List.of(hitId, missId))).willReturn(cached);
    given(contentRepository.findAllByIdWithTags(List.of(missId))).willReturn(List.of(missContent));
    given(contentCacheService.resolveMissing(List.of(missContent), cached)).willReturn(resolved);
    given(contentStatsService.getWatcherCounts(List.of(hitId, missId))).willReturn(Map.of(hitId, 2L, missId, 1L));
    given(contentCacheMapper.toDto(hitSnapshot, 2L)).willReturn(hitDto);
    given(contentCacheMapper.toDto(resolvedMiss, 1L)).willReturn(missDto);

    CursorResponse<ContentDto> result = contentService.findContents(
        null, null, null, null, null, 10, "createdAt", SortDirection.DESCENDING
    );

    assertThat(result.data()).containsExactly(hitDto, missDto);
    verify(contentRepository).findAllByIdWithTags(List.of(missId));
    verify(contentRepository, never()).findById(any());
  }

  @Test
  void findContentsUsesOpenSearchIdsForSupportedKeywordQuery() {
    CursorResponse<UUID> searchResponse = new CursorResponse<>(
        List.of(), null, null, false, 0L, "createdAt", SortDirection.DESCENDING
    );
    given(contentSearchQueryService.search(
        null, "검색어", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    )).willReturn(Optional.of(searchResponse));

    CursorResponse<ContentDto> result = contentService.findContents(
        null, "검색어", null, null, null, 10, "createdAt", SortDirection.DESCENDING
    );

    assertThat(result.data()).isEmpty();
    verify(contentRepository, never()).findContentIdsByCursor(
        any(), any(), any(), any(), any(), any(Integer.class), any(), any()
    );
  }

  @Test
  void createContent() {
    ContentCreateRequest request = createRequest("movie", "description", Set.of("action"));
    MockMultipartFile thumbnail = thumbnail();
    String thumbnailUrl = "/content-thumbnails/poster.jpg";
    ContentThumbnailFile thumbnailFile = new ContentThumbnailFile(thumbnailUrl, "poster.jpg");
    Content content = manualContent("movie", "description", thumbnailUrl, "poster.jpg", request.tags());
    UUID contentId = UUID.randomUUID();
    ReflectionTestUtils.setField(content, "id", contentId);
    ContentStats stats = ContentStats.empty();
    ContentDto expectedDto = dto(contentId, "movie", "description", thumbnailUrl, Set.of("action"), 0.0, 0);
    given(contentThumbnailService.uploadRequired(thumbnail)).willReturn(thumbnailFile);
    given(contentMapper.toEntity(request, thumbnailFile)).willReturn(content);
    given(contentRepository.save(content)).willReturn(content);
    given(contentStatsService.getStats(content)).willReturn(stats);
    given(contentMapper.toDto(content, stats)).willReturn(expectedDto);

    ContentDto result = contentService.createContent(request, thumbnail);

    assertThat(result).isEqualTo(expectedDto);
    verify(contentRepository).save(content);
    verify(contentSearchIndexService).index(contentId);
  }

  @Test
  void deleteUploadedThumbnailWhenCreateFailsAfterUpload() {
    ContentCreateRequest request = createRequest("movie", "description", Set.of("tag"));
    MockMultipartFile thumbnail = thumbnail();
    String thumbnailUrl = "/content-thumbnails/poster.jpg";
    ContentThumbnailFile thumbnailFile = new ContentThumbnailFile(thumbnailUrl, "poster.jpg");
    Content content = manualContent("movie", "description", thumbnailUrl, "poster.jpg", request.tags());
    given(contentThumbnailService.uploadRequired(thumbnail)).willReturn(thumbnailFile);
    given(contentMapper.toEntity(request, thumbnailFile)).willReturn(content);
    given(contentRepository.save(content)).willThrow(new RuntimeException("db failed"));

    assertThatThrownBy(() -> contentService.createContent(request, thumbnail))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db failed");

    verify(contentThumbnailService).delete("poster.jpg");
  }

  @Test
  void updateContentWithoutThumbnailKeepsCurrentThumbnail() {
    UUID contentId = UUID.randomUUID();
    ContentUpdateRequest request = new ContentUpdateRequest(
        "updated title",
        "updated description",
        Set.of("updated-tag")
    );
    Content content = manualContent("movie", "description", "/content-thumbnails/current.jpg", "current.jpg", Set.of("action"));
    ReflectionTestUtils.setField(content, "id", contentId);
    ContentStats stats = ContentStats.empty();
    ContentDto expectedDto = dto(contentId, "updated title", "updated description",
        "/content-thumbnails/current.jpg", Set.of("updated-tag"), 0.0, 0);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
    given(contentStatsService.getStats(content)).willReturn(stats);
    given(contentMapper.toDto(content, stats)).willReturn(expectedDto);

    ContentDto result = contentService.updateContent(contentId, request, null);

    assertThat(result).isEqualTo(expectedDto);
    assertThat(content.getTitle()).isEqualTo("updated title");
    assertThat(content.getThumbnailUrl()).isEqualTo("/content-thumbnails/current.jpg");
    verify(contentCacheService).evictAll(contentId);
    verify(contentSearchIndexService).index(contentId);
  }

  @Test
  void updateContentWithThumbnailDeletesPreviousThumbnailAfterDatabaseUpdate() {
    UUID contentId = UUID.randomUUID();
    ContentUpdateRequest request = new ContentUpdateRequest(
        "updated title",
        "updated description",
        Set.of("updated-tag")
    );
    MockMultipartFile thumbnail = thumbnail();
    ContentThumbnailFile replacement = new ContentThumbnailFile(
        "/content-thumbnails/replacement.jpg",
        "replacement.jpg"
    );
    Content content = manualContent(
        "movie",
        "description",
        "/content-thumbnails/current.jpg",
        "current.jpg",
        Set.of("action")
    );
    ReflectionTestUtils.setField(content, "id", contentId);
    ContentStats stats = ContentStats.empty();
    ContentDto expectedDto = dto(
        contentId,
        "updated title",
        "updated description",
        replacement.url(),
        Set.of("updated-tag"),
        0.0,
        0
    );
    given(contentThumbnailService.uploadOptional(thumbnail)).willReturn(replacement);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
    given(contentStatsService.getStats(content)).willReturn(stats);
    given(contentMapper.toDto(content, stats)).willReturn(expectedDto);

    ContentDto result = contentService.updateContent(contentId, request, thumbnail);

    assertThat(result).isEqualTo(expectedDto);
    assertThat(content.getThumbnailUrl()).isEqualTo(replacement.url());
    assertThat(content.getThumbnailKey()).isEqualTo(replacement.key());
    verify(contentCacheService).evictAll(contentId);
    verify(contentSearchIndexService).index(contentId);
    verify(contentThumbnailService).delete("current.jpg");
    verify(contentThumbnailService, never()).delete("replacement.jpg");
  }

  @Test
  void deleteContent() {
    UUID contentId = UUID.randomUUID();
    Content content = manualContent("movie", "description", "/content-thumbnails/current.jpg", "current.jpg", Set.of("action"));
    ReflectionTestUtils.setField(content, "id", contentId);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));

    contentService.deleteContent(contentId);

    assertThat(content.getDeletedAt()).isEqualTo(FIXED_NOW);
    verify(contentRepository, never()).delete(any());
    verify(eventPublisher).publish(new ContentSoftDeletedEvent(contentId));
    verify(contentCacheService).evictAll(contentId);
    verify(contentSearchIndexService).delete(contentId);
    verify(contentThumbnailService, never()).delete(any());
  }

  @Test
  void rejectNullUpdateRequest() {
    UUID contentId = UUID.randomUUID();

    assertThatThrownBy(() -> contentService.updateContent(contentId, null, null))
        .isInstanceOf(BaseException.class);
  }

  @Test
  void doesNotEvictCacheWhenUpdateTransactionFails() {
    UUID contentId = UUID.randomUUID();
    ContentUpdateRequest request = new ContentUpdateRequest("updated", "description", Set.of("tag"));
    given(contentRepository.findById(contentId)).willThrow(new RuntimeException("db failed"));

    assertThatThrownBy(() -> contentService.updateContent(contentId, request, null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db failed");

    verify(contentCacheService, never()).evictAll(contentId);
  }

  @Test
  void deletesReplacementThumbnailWhenUpdateTransactionFails() {
    UUID contentId = UUID.randomUUID();
    ContentUpdateRequest request = new ContentUpdateRequest("updated", "description", Set.of("tag"));
    MockMultipartFile thumbnail = thumbnail();
    ContentThumbnailFile replacement = new ContentThumbnailFile(
        "/content-thumbnails/replacement.jpg",
        "replacement.jpg"
    );
    given(contentThumbnailService.uploadOptional(thumbnail)).willReturn(replacement);
    given(contentRepository.findById(contentId)).willThrow(new RuntimeException("db failed"));

    assertThatThrownBy(() -> contentService.updateContent(contentId, request, thumbnail))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("db failed");

    verify(contentThumbnailService).delete("replacement.jpg");
    verify(contentCacheService, never()).evictAll(contentId);
  }

  private ContentCreateRequest createRequest(String title, String description, Set<String> tags) {
    return new ContentCreateRequest(ContentType.MOVIE, title, description, tags);
  }

  private Content manualContent(String title, String description, String thumbnailUrl, Set<String> tags) {
    return Content.createManual(ContentType.MOVIE, title, description, thumbnailUrl, tags);
  }

  private Content manualContent(
      String title,
      String description,
      String thumbnailUrl,
      String thumbnailKey,
      Set<String> tags
  ) {
    return Content.createManual(ContentType.MOVIE, title, description, thumbnailUrl, thumbnailKey, tags);
  }

  private ContentDto dto(
      UUID contentId,
      String title,
      String description,
      String thumbnailUrl,
      Set<String> tags,
      double averageRating,
      int reviewCount
  ) {
    return ContentDto.builder()
        .id(contentId)
        .type(ContentType.MOVIE)
        .title(title)
        .description(description)
        .thumbnailUrl(thumbnailUrl)
        .tags(tags)
        .averageRating(averageRating)
        .reviewCount(reviewCount)
        .watcherCount(0L)
        .build();
  }

  private ContentCacheSnapshot snapshot(Content content) {
    return new ContentCacheSnapshot(
        new ContentBaseCache(
            content.getId(),
            content.getTitle(),
            content.getDescription(),
            content.getType(),
            content.getSource(),
            content.getThumbnailUrl(),
            content.getTags()
        ),
        new ContentStatsCache(content.getId(), content.getAverageRating(), content.getReviewCount())
    );
  }

  private MockMultipartFile thumbnail() {
    return new MockMultipartFile(
        "thumbnail",
        "poster.jpg",
        "image/jpeg",
        "image".getBytes()
    );
  }
}
