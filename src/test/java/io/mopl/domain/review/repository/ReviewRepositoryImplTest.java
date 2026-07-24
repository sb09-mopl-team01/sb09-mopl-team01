package io.mopl.domain.review.repository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.mopl.domain.review.entity.Review;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(ReviewRepositoryImplTest.QuerydslConfig.class)
class ReviewRepositoryImplTest {

  @Autowired
  private EntityManager em;

  @Autowired
  private JPAQueryFactory queryFactory;

  private ReviewRepositoryImpl reviewRepository;

  private UUID contentId;

  @TestConfiguration
  static class QuerydslConfig {
    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager em) {
      return new JPAQueryFactory(em);
    }
  }

  @BeforeEach
  void setUp() {
    reviewRepository = new ReviewRepositoryImpl(queryFactory);
    contentId = UUID.randomUUID();

  }

  @Test
  @DisplayName("커서가 모두 Null일 때 첫 페이지 정상 조회")
  void findReviewsByCursor_FirstPage() {

    String cursor = null;
    UUID idAfter = null;

    List<Review> result = reviewRepository.findReviewsByCursor(
        contentId, cursor, idAfter, 10, "DESCENDING", "createdAt"
    );

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("커서 값 중 하나만 Null이면 예외 발생")
  void findReviewsByCursor_InvalidCursorThrowsException() {

    String cursor = Instant.now().toString();
    UUID idAfter = null;

    assertThatThrownBy(() -> reviewRepository.findReviewsByCursor(contentId, cursor, idAfter, 10, "DESCENDING", "createdAt"))
        .hasMessageContaining("잘못된 요청입니다");
  }

  @Test
  @DisplayName("Rating 정렬 ASC (오름차순) 페이징 - 커버리지: sortBy rating, ASCENDING")
  void findReviewsByCursor_RatingAsc() {

    String cursor = "3.5";
    UUID idAfter = UUID.randomUUID();

    List<Review> result = reviewRepository.findReviewsByCursor(
        contentId, cursor, idAfter, 10, "ASCENDING", "rating"
    );

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("Rating 정렬 DESC (내림차순) 페이징")
  void findReviewsByCursor_RatingDesc() {

    String cursor = "4.5";
    UUID idAfter = UUID.randomUUID();

    List<Review> result = reviewRepository.findReviewsByCursor(
        contentId, cursor, idAfter, 10, "DESCENDING", "rating"
    );

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("CreatedAt 정렬 ASC (오름차순) 페이징")
  void findReviewsByCursor_CreatedAtAsc() {

    String cursor = Instant.now().minusSeconds(1000).toString();
    UUID idAfter = UUID.randomUUID();

    List<Review> result = reviewRepository.findReviewsByCursor(
        contentId, cursor, idAfter, 10, "ASCENDING", "createdAt"
    );

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("ContentId가 Null인 경우의 조회")
  void findReviewsByCursor_NullContentId() {

    String cursor = null;
    UUID idAfter = null;

    List<Review> result = reviewRepository.findReviewsByCursor(
        null, cursor, idAfter, 10, "DESCENDING", "createdAt"
    );

    assertThat(result).isNotNull();
  }
}