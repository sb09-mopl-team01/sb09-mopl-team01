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
import io.mopl.global.exception.BaseException;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

  @InjectMocks
  private ContentService contentService;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentStatsService contentStatsService;

  @Mock
  private ContentMapper contentMapper;

  @Mock
  private ContentThumbnailService contentThumbnailService;

  @Test
  @DisplayName("콘텐츠 단건 조회 시 집계값을 조합해 DTO로 변환한다")
  void findContent() {
    Content content = Content.createManual(
        ContentType.MOVIE,
        "영화",
        "영화 설명",
        "https://image.example.com/movie.jpg",
        List.of("액션")
    );
    UUID contentId = UUID.randomUUID();
    ReflectionTestUtils.setField(content, "id", contentId);
    content.updateReviewStats(4.5, 2);
    ContentStats stats = new ContentStats(4.5, 2, 0L);
    ContentDto expectedDto = ContentDto.builder()
        .id(contentId)
        .type(ContentType.MOVIE)
        .title("영화")
        .description("영화 설명")
        .thumbnailUrl("https://image.example.com/movie.jpg")
        .tags(java.util.Set.of("액션"))
        .averageRating(4.5)
        .reviewCount(2)
        .watcherCount(0L)
        .build();
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
    given(contentStatsService.getStats(content)).willReturn(stats);
    given(contentMapper.toDto(content, stats)).willReturn(expectedDto);

    ContentDto result = contentService.findContent(contentId);

    assertThat(result).isEqualTo(expectedDto);
  }

  @Test
  @DisplayName("존재하지 않는 콘텐츠 단건 조회는 예외로 처리한다")
  void rejectUnknownContent() {
    UUID contentId = UUID.randomUUID();
    given(contentRepository.findById(contentId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.findContent(contentId))
        .isInstanceOf(BaseException.class);
  }

  @Test
  @DisplayName("콘텐츠 목록 조회 시 Content 저장 집계값을 DTO에 조합한다")
  void findContents() {
    Content content = Content.createManual(
        ContentType.MOVIE,
        "영화",
        "영화 설명",
        null,
        List.of("액션")
    );
    UUID contentId = UUID.randomUUID();
    ReflectionTestUtils.setField(content, "id", contentId);
    content.updateReviewStats(4.0, 3);
    ContentStats stats = new ContentStats(4.0, 3, 0L);
    ContentDto expectedDto = ContentDto.builder()
        .id(contentId)
        .type(ContentType.MOVIE)
        .title("영화")
        .description("영화 설명")
        .thumbnailUrl(null)
        .tags(java.util.Set.of("액션"))
        .averageRating(4.0)
        .reviewCount(3)
        .watcherCount(0L)
        .build();
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
        "영화",
        List.of("액션"),
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
        "영화",
        List.of("액션"),
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
  @DisplayName("관리자 콘텐츠 생성 시 MANUAL 콘텐츠를 저장하고 DTO로 반환한다")
  void createContent() {
    ContentCreateRequest request = new ContentCreateRequest(
        ContentType.MOVIE,
        "영화",
        "영화 설명",
        java.util.Set.of("액션")
    );
    MockMultipartFile thumbnail = new MockMultipartFile(
        "thumbnail",
        "poster.jpg",
        "image/jpeg",
        "image".getBytes()
    );
    String thumbnailUrl = "/content-thumbnails/poster.jpg";
    Content content = Content.createManual(
        ContentType.MOVIE,
        "영화",
        "영화 설명",
        thumbnailUrl,
        request.tags()
    );
    UUID contentId = UUID.randomUUID();
    ReflectionTestUtils.setField(content, "id", contentId);
    ContentStats stats = new ContentStats(0.0, 0, 0L);
    ContentDto expectedDto = ContentDto.builder()
        .id(contentId)
        .type(ContentType.MOVIE)
        .title("영화")
        .description("영화 설명")
        .thumbnailUrl(thumbnailUrl)
        .tags(java.util.Set.of("액션"))
        .averageRating(0.0)
        .reviewCount(0)
        .watcherCount(0L)
        .build();
    given(contentThumbnailService.uploadRequired(thumbnail)).willReturn(thumbnailUrl);
    given(contentMapper.toEntity(request, thumbnailUrl)).willReturn(content);
    given(contentRepository.save(content)).willReturn(content);
    given(contentStatsService.getStats(content)).willReturn(stats);
    given(contentMapper.toDto(content, stats)).willReturn(expectedDto);

    ContentDto result = contentService.createContent(request, thumbnail);

    assertThat(result).isEqualTo(expectedDto);
    verify(contentRepository).save(content);
  }

  @Test
  @DisplayName("관리자 콘텐츠 생성 트랜잭션 롤백 시 업로드한 썸네일을 삭제한다")
  void deleteUploadedThumbnailWhenCreateTransactionRollsBack() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      ContentCreateRequest request = new ContentCreateRequest(
          ContentType.MOVIE,
          "영화",
          "영화 설명",
          java.util.Set.of("액션")
      );
      MockMultipartFile thumbnail = new MockMultipartFile(
          "thumbnail",
          "poster.jpg",
          "image/jpeg",
          "image".getBytes()
      );
      String thumbnailUrl = "/content-thumbnails/poster.jpg";
      Content content = Content.createManual(
          ContentType.MOVIE,
          "영화",
          "영화 설명",
          thumbnailUrl,
          request.tags()
      );
      UUID contentId = UUID.randomUUID();
      ReflectionTestUtils.setField(content, "id", contentId);
      ContentStats stats = new ContentStats(0.0, 0, 0L);
      ContentDto expectedDto = ContentDto.builder()
          .id(contentId)
          .type(ContentType.MOVIE)
          .title("영화")
          .description("영화 설명")
          .thumbnailUrl(thumbnailUrl)
          .tags(java.util.Set.of("액션"))
          .averageRating(0.0)
          .reviewCount(0)
          .watcherCount(0L)
          .build();
      given(contentThumbnailService.uploadRequired(thumbnail)).willReturn(thumbnailUrl);
      given(contentMapper.toEntity(request, thumbnailUrl)).willReturn(content);
      given(contentRepository.save(content)).willReturn(content);
      given(contentStatsService.getStats(content)).willReturn(stats);
      given(contentMapper.toDto(content, stats)).willReturn(expectedDto);

      contentService.createContent(request, thumbnail);

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(synchronization ->
              synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

      verify(contentThumbnailService).delete(thumbnailUrl);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("관리자 콘텐츠 수정 시 thumbnail이 없으면 기존 썸네일을 유지한다")
  void updateContentWithoutThumbnailKeepsCurrentThumbnail() {
    UUID contentId = UUID.randomUUID();
    ContentUpdateRequest request = new ContentUpdateRequest(
        "수정 제목",
        "수정 설명",
        java.util.Set.of("수정태그")
    );
    Content content = Content.createManual(
        ContentType.MOVIE,
        "영화",
        "영화 설명",
        "/content-thumbnails/current.jpg",
        List.of("액션")
    );
    ReflectionTestUtils.setField(content, "id", contentId);
    ContentStats stats = new ContentStats(0.0, 0, 0L);
    ContentDto expectedDto = ContentDto.builder()
        .id(contentId)
        .type(ContentType.MOVIE)
        .title("수정 제목")
        .description("수정 설명")
        .thumbnailUrl("/content-thumbnails/current.jpg")
        .tags(java.util.Set.of("수정태그"))
        .averageRating(0.0)
        .reviewCount(0)
        .watcherCount(0L)
        .build();
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));
    given(contentThumbnailService.uploadOptional(null, "/content-thumbnails/current.jpg"))
        .willReturn("/content-thumbnails/current.jpg");
    given(contentStatsService.getStats(content)).willReturn(stats);
    given(contentMapper.toDto(content, stats)).willReturn(expectedDto);

    ContentDto result = contentService.updateContent(contentId, request, null);

    assertThat(result).isEqualTo(expectedDto);
    assertThat(content.getTitle()).isEqualTo("수정 제목");
    assertThat(content.getThumbnailUrl()).isEqualTo("/content-thumbnails/current.jpg");
  }

  @Test
  @DisplayName("관리자 콘텐츠 삭제 시 콘텐츠와 썸네일을 삭제한다")
  void deleteContent() {
    UUID contentId = UUID.randomUUID();
    Content content = Content.createManual(
        ContentType.MOVIE,
        "영화",
        "영화 설명",
        "/content-thumbnails/current.jpg",
        List.of("액션")
    );
    ReflectionTestUtils.setField(content, "id", contentId);
    given(contentRepository.findById(contentId)).willReturn(Optional.of(content));

    contentService.deleteContent(contentId);

    verify(contentRepository).delete(content);
    verify(contentThumbnailService).delete("/content-thumbnails/current.jpg");
  }

  @Test
  @DisplayName("관리자 콘텐츠 수정 시 request가 null이면 예외로 처리한다")
  void rejectNullUpdateRequest() {
    UUID contentId = UUID.randomUUID();

    assertThatThrownBy(() -> contentService.updateContent(contentId, null, null))
        .isInstanceOf(BaseException.class);
  }
}
