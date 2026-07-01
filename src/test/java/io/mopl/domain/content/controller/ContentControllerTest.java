package io.mopl.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ContentDto;
import io.mopl.domain.content.dto.request.ContentCreateRequest;
import io.mopl.domain.content.dto.request.ContentUpdateRequest;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.service.ContentService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

  @InjectMocks
  private ContentController contentController;

  @Mock
  private ContentService contentService;

  @Test
  @DisplayName("GET /api/contents/{id} - 콘텐츠 단건 조회 성공")
  void findContent() {
    UUID contentId = UUID.randomUUID();
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
    given(contentService.findContent(contentId)).willReturn(expectedDto);

    ResponseEntity<ContentDto> response = contentController.findContent(contentId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  @Test
  @DisplayName("GET /api/contents - 콘텐츠 목록 조회 요청 파라미터를 서비스로 전달한다")
  void findContents() {
    CursorResponse<ContentDto> expectedResponse = new CursorResponse<>(
        List.of(),
        null,
        null,
        false,
        0L,
        "createdAt",
        SortDirection.DESCENDING
    );
    given(contentService.findContents(
        eq(ContentType.MOVIE),
        eq("우주"),
        eq(List.of("SF")),
        eq(null),
        eq(null),
        eq(10),
        eq("createdAt"),
        eq(SortDirection.DESCENDING)
    )).willReturn(expectedResponse);

    ResponseEntity<CursorResponse<ContentDto>> response = contentController.findContents(
        "movie",
        "우주",
        List.of("SF"),
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedResponse);
  }

  @Test
  @DisplayName("GET /api/contents - 잘못된 typeEqual은 400 예외로 처리한다")
  void rejectInvalidTypeEqual() {
    assertThatThrownBy(() -> contentController.findContents(
        "invalid",
        null,
        null,
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    )).isInstanceOf(BaseException.class);
  }

  @Test
  @DisplayName("GET /api/contents - 잘못된 limit 또는 sortBy는 400 예외로 처리한다")
  void rejectInvalidPageParams() {
    assertThatThrownBy(() -> contentController.findContents(
        null,
        null,
        null,
        null,
        null,
        0,
        "createdAt",
        SortDirection.DESCENDING
    )).isInstanceOf(BaseException.class);

    assertThatThrownBy(() -> contentController.findContents(
        null,
        null,
        null,
        null,
        null,
        10,
        "title",
        SortDirection.DESCENDING
    )).isInstanceOf(BaseException.class);
  }

  @Test
  @DisplayName("GET /api/contents - rate와 watcherCount 정렬 요청을 허용한다")
  void allowStatsSortBy() {
    CursorResponse<ContentDto> expectedResponse = new CursorResponse<>(
        List.of(),
        null,
        null,
        false,
        0L,
        "rate",
        SortDirection.DESCENDING
    );
    given(contentService.findContents(
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(10),
        eq("rate"),
        eq(SortDirection.DESCENDING)
    )).willReturn(expectedResponse);

    ResponseEntity<CursorResponse<ContentDto>> response = contentController.findContents(
        null,
        null,
        null,
        null,
        null,
        10,
        "rate",
        SortDirection.DESCENDING
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedResponse);
  }

  @Test
  @DisplayName("POST /api/contents - 관리자 콘텐츠 생성 성공 시 201을 반환한다")
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
    ContentDto expectedDto = ContentDto.builder()
        .id(UUID.randomUUID())
        .type(ContentType.MOVIE)
        .title("영화")
        .description("영화 설명")
        .thumbnailUrl("/content-thumbnails/poster.jpg")
        .tags(java.util.Set.of("액션"))
        .averageRating(0.0)
        .reviewCount(0)
        .watcherCount(0L)
        .build();
    given(contentService.createContent(request, thumbnail)).willReturn(expectedDto);

    ResponseEntity<ContentDto> response = contentController.createContent(request, thumbnail);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  @Test
  @DisplayName("PATCH /api/contents/{id} - 관리자 콘텐츠 수정 성공 시 200을 반환한다")
  void updateContent() {
    UUID contentId = UUID.randomUUID();
    ContentUpdateRequest request = new ContentUpdateRequest(
        "수정 제목",
        "수정 설명",
        java.util.Set.of("수정태그")
    );
    MockMultipartFile thumbnail = new MockMultipartFile(
        "thumbnail",
        "poster.jpg",
        "image/jpeg",
        "image".getBytes()
    );
    ContentDto expectedDto = ContentDto.builder()
        .id(contentId)
        .type(ContentType.MOVIE)
        .title("수정 제목")
        .description("수정 설명")
        .thumbnailUrl("/content-thumbnails/poster.jpg")
        .tags(java.util.Set.of("수정태그"))
        .averageRating(0.0)
        .reviewCount(0)
        .watcherCount(0L)
        .build();
    given(contentService.updateContent(contentId, request, thumbnail)).willReturn(expectedDto);

    ResponseEntity<ContentDto> response = contentController.updateContent(contentId, request, thumbnail);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  @Test
  @DisplayName("DELETE /api/contents/{id} - 관리자 콘텐츠 삭제 성공 시 200을 반환한다")
  void deleteContent() {
    UUID contentId = UUID.randomUUID();

    ResponseEntity<Void> response = contentController.deleteContent(contentId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(contentService).deleteContent(contentId);
  }
}
