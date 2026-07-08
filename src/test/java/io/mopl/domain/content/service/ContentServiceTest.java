package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
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

  private ContentService contentService;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentStatsService contentStatsService;

  @Mock
  private ContentMapper contentMapper;

  @Mock
  private ContentThumbnailService contentThumbnailService;

  @BeforeEach
  void setUp() {
    contentService = new ContentService(
        contentRepository,
        contentStatsService,
        contentMapper,
        contentThumbnailService,
        new ResourcelessTransactionManager()
    );
  }

  @Test
  void findContent() {
    Content content = manualContent("movie", "description", "https://image.example.com/movie.jpg", Set.of("action"));
    UUID contentId = UUID.randomUUID();
    ReflectionTestUtils.setField(content, "id", contentId);
    content.updateReviewStats(4.5, 2);
    ContentStats stats = new ContentStats(4.5, 2, 0L);
    ContentDto expectedDto = dto(contentId, "movie", "description", "https://image.example.com/movie.jpg",
        Set.of("action"), 4.5, 2);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
    given(contentStatsService.getStats(content)).willReturn(stats);
    given(contentMapper.toDto(content, stats)).willReturn(expectedDto);

    ContentDto result = contentService.findContent(contentId);

    assertThat(result).isEqualTo(expectedDto);
  }

  @Test
  void rejectUnknownContent() {
    UUID contentId = UUID.randomUUID();
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
    ContentStats stats = new ContentStats(4.0, 3, 0L);
    ContentDto expectedDto = dto(contentId, "movie", "description", null, Set.of("action"), 4.0, 3);
    CursorResponse<Content> repositoryResponse = new CursorResponse<>(
        List.of(content),
        "2026-06-29T00:00:00Z",
        contentId,
        true,
        1L,
        "createdAt",
        SortDirection.DESCENDING
    );
    given(contentRepository.findContentsByCursor(
        ContentType.MOVIE,
        "movie",
        List.of("action"),
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    )).willReturn(repositoryResponse);
    given(contentStatsService.getStatsByContents(List.of(content)))
        .willReturn(java.util.Map.of(content.getId(), stats));
    given(contentMapper.toDto(content, stats)).willReturn(expectedDto);

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
  }

  @Test
  void deleteContent() {
    UUID contentId = UUID.randomUUID();
    Content content = manualContent("movie", "description", "/content-thumbnails/current.jpg", "current.jpg", Set.of("action"));
    ReflectionTestUtils.setField(content, "id", contentId);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));

    contentService.deleteContent(contentId);

    verify(contentRepository).delete(content);
    verify(contentThumbnailService).delete("current.jpg");
  }

  @Test
  void rejectNullUpdateRequest() {
    UUID contentId = UUID.randomUUID();

    assertThatThrownBy(() -> contentService.updateContent(contentId, null, null))
        .isInstanceOf(BaseException.class);
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

  private MockMultipartFile thumbnail() {
    return new MockMultipartFile(
        "thumbnail",
        "poster.jpg",
        "image/jpeg",
        "image".getBytes()
    );
  }
}
