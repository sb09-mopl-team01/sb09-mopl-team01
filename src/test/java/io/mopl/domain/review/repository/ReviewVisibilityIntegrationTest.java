package io.mopl.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.review.entity.Review;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.config.QueryDslConfig;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(QueryDslConfig.class)
class ReviewVisibilityIntegrationTest {

  @Autowired
  private ReviewRepository reviewRepository;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  void deletedContentReviewsRemainStoredButAreExcludedFromUserQueries() {
    User author = userRepository.save(User.builder()
        .email("review-author@test.com")
        .passwordHash("password123")
        .name("Review Author")
        .role(Role.USER)
        .build());
    Content content = contentRepository.save(Content.createManual(
        ContentType.MOVIE,
        "Movie",
        "Movie description",
        null,
        Set.of("영화")
    ));
    Review review = reviewRepository.save(Review.builder()
        .author(author)
        .content(content)
        .text("Review")
        .rating(4.0)
        .build());
    reviewRepository.flush();

    assertThat(reviewRepository.findReviewsByCursor(
        content.getId(), null, null, 10, "ASCENDING", "createdAt"
    )).containsExactly(review);
    assertThat(reviewRepository.countVisibleByContentId(content.getId())).isEqualTo(1L);
    assertThat(reviewRepository.findActiveById(review.getId())).contains(review);

    content.softDelete(Instant.parse("2026-07-22T00:00:00Z"));
    contentRepository.flush();

    assertThat(reviewRepository.findReviewsByCursor(
        content.getId(), null, null, 10, "ASCENDING", "createdAt"
    )).isEmpty();
    assertThat(reviewRepository.countVisibleByContentId(content.getId())).isZero();
    assertThat(reviewRepository.findActiveById(review.getId())).isEmpty();
    assertThat(reviewRepository.findById(review.getId())).contains(review);
  }
}
