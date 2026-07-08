package io.mopl.domain.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.review.dto.ReviewDto;
import io.mopl.domain.review.dto.request.ReviewCreateRequest;
import io.mopl.domain.review.dto.request.ReviewUpdateRequest;
import io.mopl.domain.review.service.ReviewService;
import io.mopl.domain.user.entity.User;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import io.mopl.global.security.MoplUserDetails;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewControllerTest {

  @InjectMocks
  private ReviewController reviewController;

  @Mock
  private ReviewService reviewService;

  private MoplUserDetails mockUserDetails;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    User mockUser = mock(User.class);
    given(mockUser.getId()).willReturn(userId);

    mockUserDetails = mock(MoplUserDetails.class);
    given(mockUserDetails.getUser()).willReturn(mockUser);
  }

  @Test
  @DisplayName("POST /api/reviews - 리뷰 생성 성공")
  void createReview() {
    ReviewCreateRequest request = new ReviewCreateRequest();
    ReflectionTestUtils.setField(request, "contentId", UUID.randomUUID());
    ReflectionTestUtils.setField(request, "text", "좋아요");
    ReflectionTestUtils.setField(request, "rating", 5.0);

    ReviewDto expectedDto = mock(ReviewDto.class);
    given(reviewService.createReview(eq(userId), any())).willReturn(expectedDto);

    ResponseEntity<ReviewDto> response = reviewController.createReview(mockUserDetails, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  @Test
  @DisplayName("PATCH /api/reviews/{id} - 리뷰 수정 성공")
  void updateReview() {
    UUID reviewId = UUID.randomUUID();
    ReviewUpdateRequest request = new ReviewUpdateRequest();
    ReflectionTestUtils.setField(request, "text", "수정");
    ReflectionTestUtils.setField(request, "rating", 4.0);

    ReviewDto expectedDto = mock(ReviewDto.class);
    given(reviewService.updateReview(eq(userId), eq(reviewId), any())).willReturn(expectedDto);

    ResponseEntity<ReviewDto> response = reviewController.updateReview(mockUserDetails, reviewId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  @Test
  @DisplayName("GET /api/reviews - 리뷰 목록 조회 성공")
  void findReviews() {
    // 1. 서비스가 반환할 가짜 데이터 생성
    CursorResponse<ReviewDto> fakeResponse = new CursorResponse<>(
        List.of(mock(ReviewDto.class)), "nextCursor", null, true, 1L, "createdAt", SortDirection.DESCENDING
    );

    // 2. 서비스 Mock 설정
    given(reviewService.findReviews(any(), any(), any(), eq(10), eq("DESCENDING"), eq("createdAt")))
        .willReturn(fakeResponse);

    // 3. 컨트롤러 호출
    ResponseEntity<CursorResponse<ReviewDto>> response = reviewController.findReviews(
        null, null, null, 10, "DESCENDING", "createdAt"
    );

    // 4. 검증
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(fakeResponse);
  }

  @Test
  @DisplayName("DELETE /api/reviews/{id} - 리뷰 삭제 성공")
  void deleteReview() {
    UUID reviewId = UUID.randomUUID();

    ResponseEntity<Void> response = reviewController.deleteReview(mockUserDetails, reviewId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(reviewService).deleteReview(userId, reviewId);
  }


}