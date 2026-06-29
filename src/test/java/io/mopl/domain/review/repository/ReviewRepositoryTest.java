package io.mopl.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.review.entity.Review;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ReviewRepositoryTest {

  @Mock private ReviewRepository reviewRepository;

  @Test
  @DisplayName("콘텐츠 ID로 페이징된 리뷰 목록을 조회한다")
  void findByContentId() {
    UUID contentId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 10);
    Page<Review> expectedPage = new PageImpl<>(List.of(mock(Review.class)), pageable, 1);

    given(reviewRepository.findByContentId(contentId, pageable)).willReturn(expectedPage);

    Page<Review> result = reviewRepository.findByContentId(contentId, pageable);

    assertThat(result.getContent()).hasSize(1);
    verify(reviewRepository).findByContentId(contentId, pageable);
  }

  @Test
  @DisplayName("작성자 ID와 콘텐츠 ID로 리뷰 존재 여부를 확인한다")
  void existsByAuthorIdAndContentId() {
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    given(reviewRepository.existsByAuthorIdAndContentId(authorId, contentId)).willReturn(true);

    boolean exists = reviewRepository.existsByAuthorIdAndContentId(authorId, contentId);

    assertThat(exists).isTrue();
  }

  @Test
  @DisplayName("콘텐츠 ID로 평균 평점을 계산한다")
  void calculateAverageRatingByContentId() {
    UUID contentId = UUID.randomUUID();
    given(reviewRepository.calculateAverageRatingByContentId(contentId)).willReturn(4.5);

    double result = reviewRepository.calculateAverageRatingByContentId(contentId);

    assertThat(result).isEqualTo(4.5);
  }

  @Test
  @DisplayName("콘텐츠 ID에 해당하는 전체 리뷰 개수를 조회한다")
  void countByContentId() {
    UUID contentId = UUID.randomUUID();
    given(reviewRepository.countByContentId(contentId)).willReturn(15L);

    long result = reviewRepository.countByContentId(contentId);

    assertThat(result).isEqualTo(15L);
  }
}