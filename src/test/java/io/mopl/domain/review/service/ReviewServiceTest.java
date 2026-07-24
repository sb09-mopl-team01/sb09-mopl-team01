package io.mopl.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.event.ReviewStatsChangedEvent;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.review.dto.ReviewDto;
import io.mopl.domain.review.dto.request.ReviewCreateRequest;
import io.mopl.domain.review.dto.request.ReviewUpdateRequest;
import io.mopl.domain.review.entity.Review;
import io.mopl.domain.review.mapper.ReviewMapper;

import io.mopl.domain.review.repository.ReviewRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  @InjectMocks
  private ReviewService reviewService;

  @Mock
  private ReviewRepository reviewRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private ContentRepository contentRepository;
  @Mock
  private ReviewMapper reviewMapper;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private ReviewCreateRequest createReviewRequest(UUID contentId, String text, Double rating) {
    ReviewCreateRequest request = new ReviewCreateRequest();
    ReflectionTestUtils.setField(request, "contentId", contentId);
    ReflectionTestUtils.setField(request, "text", text);
    ReflectionTestUtils.setField(request, "rating", rating);
    return request;
  }

  @Test
  @DisplayName("리뷰 생성 성공")
  void createReview_Success() {
    UUID userId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    ReviewCreateRequest request = createReviewRequest(contentId, "중복", 5.0);
    Content content = mock(Content.class);
    given(content.getId()).willReturn(contentId);

    given(userRepository.findById(userId)).willReturn(Optional.of(mock(User.class)));
    given(contentRepository.findById(request.getContentId())).willReturn(Optional.of(content));
    given(reviewRepository.existsByAuthorIdAndContentId(userId, request.getContentId())).willReturn(
        false);

    Review savedReview = mock(Review.class);
    given(savedReview.getId()).willReturn(UUID.randomUUID());
    given(reviewRepository.save(any(Review.class))).willReturn(savedReview);

    given(reviewMapper.toDto(any())).willReturn(mock(ReviewDto.class));

    reviewService.createReview(userId, request);

    verify(reviewRepository).save(any(Review.class));
    verify(eventPublisher).publishEvent(new ReviewStatsChangedEvent(contentId));
  }

  @Test
  @DisplayName("리뷰 생성 실패: 중복 리뷰")
  void createReview_Fail_AlreadyReviewed() {
    UUID userId = UUID.randomUUID();
    ReviewCreateRequest request = createReviewRequest(UUID.randomUUID(), "중복", 5.0);

    given(userRepository.findById(userId)).willReturn(Optional.of(mock(User.class)));
    given(contentRepository.findById(request.getContentId())).willReturn(
        Optional.of(mock(Content.class)));
    given(reviewRepository.existsByAuthorIdAndContentId(userId, request.getContentId())).willReturn(
        true);

    BaseException ex = assertThrows(BaseException.class,
        () -> reviewService.createReview(userId, request));
    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ALREADY_REVIEWED);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("리뷰 수정 성공")
  void updateReview_Success() {
    UUID userId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    ReviewUpdateRequest request = new ReviewUpdateRequest();
    ReflectionTestUtils.setField(request, "text", "수정");
    ReflectionTestUtils.setField(request, "rating", 4.0);

    User user = mock(User.class);
    given(user.getId()).willReturn(userId);
    UUID contentId = UUID.randomUUID();
    Content content = mock(Content.class);
    given(content.getId()).willReturn(contentId);

    Review review = mock(Review.class);
    given(review.getAuthor()).willReturn(user);
    given(review.getContent()).willReturn(content);
    given(reviewRepository.findActiveById(reviewId)).willReturn(Optional.of(review));
    given(reviewMapper.toDto(review)).willReturn(mock(ReviewDto.class));

    reviewService.updateReview(userId, reviewId, request);

    verify(review).update("수정", 4.0);
    verify(eventPublisher).publishEvent(new ReviewStatsChangedEvent(contentId));
  }

  @Test
  @DisplayName("리뷰 목록 조회 성공")
  void findReviews_Success() {
    UUID contentId = UUID.randomUUID();

    given(reviewRepository.findReviewsByCursor(
        any(UUID.class), any(), any(), anyInt(), anyString(), anyString()))
        .willReturn(List.of(mock(Review.class)));

    given(reviewRepository.countVisibleByContentId(contentId)).willReturn(1L);

    CursorResponse<ReviewDto> response = reviewService.findReviews(
        contentId, null, null, 10, "ASCENDING", "createdAt"
    );

    assertThat(response.data()).hasSize(1);
  }

  @Test
  @DisplayName("리뷰 삭제 성공")
  void deleteReview_Success() {
    UUID userId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    User user = mock(User.class);
    given(user.getId()).willReturn(userId);
    UUID contentId = UUID.randomUUID();
    Content content = mock(Content.class);
    given(content.getId()).willReturn(contentId);

    Review review = mock(Review.class);
    given(review.getAuthor()).willReturn(user);
    given(review.getContent()).willReturn(content);
    given(reviewRepository.findActiveById(reviewId)).willReturn(Optional.of(review));

    reviewService.deleteReview(userId, reviewId);

    verify(reviewRepository).delete(review);
    verify(eventPublisher).publishEvent(new ReviewStatsChangedEvent(contentId));
  }

  @Test
  @DisplayName("리뷰 수정 실패: 작성자가 아닌 유저의 요청")
  void updateReview_Fail_Unauthorized() {
    UUID userId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    ReviewUpdateRequest request = new ReviewUpdateRequest();

    User author = mock(User.class);
    given(author.getId()).willReturn(authorId);

    Review review = mock(Review.class);
    given(review.getAuthor()).willReturn(author);
    given(reviewRepository.findActiveById(reviewId)).willReturn(Optional.of(review));

    BaseException ex = assertThrows(BaseException.class,
        () -> reviewService.updateReview(userId, reviewId, request));

    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  @DisplayName("리뷰 삭제 실패: 작성자가 아닌 유저의 요청")
  void deleteReview_Fail_Unauthorized() {
    UUID userId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    User author = mock(User.class);
    given(author.getId()).willReturn(authorId);

    Review review = mock(Review.class);
    given(review.getAuthor()).willReturn(author);
    given(reviewRepository.findActiveById(reviewId)).willReturn(Optional.of(review));

    BaseException ex = assertThrows(BaseException.class,
        () -> reviewService.deleteReview(userId, reviewId));

    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
  }

  @Test
  @DisplayName("리뷰 목록 조회 성공: 다음 페이지가 존재할 때")
  void findReviews_Success_HasNext_SortByRating() {
    UUID contentId = UUID.randomUUID();
    int limit = 1;

    Review mockReview = mock(Review.class);
    given(mockReview.getId()).willReturn(UUID.randomUUID());
    given(mockReview.getRating()).willReturn(4.5);

    given(reviewRepository.findReviewsByCursor(
        any(UUID.class), any(), any(), anyInt(), anyString(), anyString()))
        .willReturn(List.of(mockReview));
    given(reviewRepository.countVisibleByContentId(contentId)).willReturn(10L);

    CursorResponse<ReviewDto> response = reviewService.findReviews(
        contentId, null, null, limit, "DESCENDING", "rating"
    );

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo("4.5");
  }

  @Test
  @DisplayName("리뷰 목록 조회 성공: 다음 페이지가 존재할 때")
  void findReviews_Success_HasNext_SortByCreatedAt() {
    UUID contentId = UUID.randomUUID();
    int limit = 1;
    Instant now = Instant.now();

    Review mockReview = mock(Review.class);
    given(mockReview.getId()).willReturn(UUID.randomUUID());
    given(mockReview.getCreatedAt()).willReturn(now);

    given(reviewRepository.findReviewsByCursor(
        any(UUID.class), any(), any(), anyInt(), anyString(), anyString()))
        .willReturn(List.of(mockReview));
    given(reviewRepository.countVisibleByContentId(contentId)).willReturn(10L);

    CursorResponse<ReviewDto> response = reviewService.findReviews(
        contentId, null, null, limit, "DESCENDING", "createdAt"
    );

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo(now.toString());
  }
}
