package io.mopl.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(UserRepositoryTest.QueryDslTestConfig.class)
class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @TestConfiguration
  static class QueryDslTestConfig {
    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
      return new JPAQueryFactory(entityManager);
    }
  }

  @Test
  @DisplayName("이메일 존재 여부 확인 ")
  void existsByEmail() {
    User user = User.builder()
        .email("duplicate@example.com")
        .passwordHash("hash")
        .name("홍길동")
        .build();
    userRepository.save(user);

    boolean exists = userRepository.existsByEmail("duplicate@example.com");
    boolean notExists = userRepository.existsByEmail("test@example.com");

    assertThat(exists).isTrue();
    assertThat(notExists).isFalse();
  }

  @Test
  @DisplayName("커서 페이징: emailLike 조건으로 필터링, 이름 오름차순 정렬")
  void findUsersByCursor_WithEmailLike_And_SortByNameAsc() {
    User user1 = User.builder().email("test1@gmail.com").passwordHash("h").name("가나다").build();
    User user2 = User.builder().email("test2@naver.com").passwordHash("h").name("마바사").build();
    User user3 = User.builder().email("test3@gmail.com").passwordHash("h").name("아자차").build();

    userRepository.save(user1);
    userRepository.save(user2);
    userRepository.save(user3);

    CursorResponse<User> response = userRepository.findUsersByCursor(
        "gmail", null, null, null, null, 1, "name", SortDirection.ASCENDING
    );

    assertThat(response.data()).hasSize(1);

    assertThat(response.data().get(0).getName()).isEqualTo("가나다");

    assertThat(response.hasNext()).isTrue();

    assertThat(response.nextCursor()).isEqualTo("가나다");
    assertThat(response.nextIdAfter()).isEqualTo(user1.getId());

    assertThat(response.totalCount()).isEqualTo(2L);
  }
}
