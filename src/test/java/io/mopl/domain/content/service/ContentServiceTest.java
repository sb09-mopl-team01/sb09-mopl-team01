package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import io.mopl.domain.content.dto.ContentDto;
import io.mopl.domain.content.dto.ContentStats;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
}
