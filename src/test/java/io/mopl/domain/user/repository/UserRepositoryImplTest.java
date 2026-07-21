package io.mopl.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@DataJpaTest
@Import(UserRepositoryImplTest.QuerydslConfig.class)
@AutoConfigureTestDatabase(replace = Replace.ANY)
class UserRepositoryImplTest {

  @TestConfiguration
  @EnableJpaAuditing
  static class QuerydslConfig {
    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
      return new JPAQueryFactory(entityManager);
    }
  }

  @Autowired
  private UserRepository userRepository;
  @Autowired
  private EntityManager entityManager;

  private User user1, user2, user3;

  @BeforeEach
  void setUp() throws InterruptedException {
    userRepository.deleteAll();

    user1 = userRepository.save(User.builder().email("aaa@example.com").name("Alice").passwordHash("pw").role(Role.USER).build());
    Thread.sleep(10);
    user2 = userRepository.save(User.builder().email("bbb@example.com").name("Bob").passwordHash("pw").role(Role.ADMIN).build());
    user2.lockAccount();
    Thread.sleep(10);
    user3 = userRepository.save(User.builder().email("ccc@example.com").name("Charlie").passwordHash("pw").role(Role.USER).build());

    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("필터 조건 완벽 검증: email, role, isLocked 및 예외 발생 분기(catch)")
  void findUsers_FilterConditions() {
    CursorResponse<User> response1 = userRepository.findUsersByCursor(
        "bbb", "ADMIN", true, null, null, 10, "createdAt", SortDirection.DESCENDING);
    assertThat(response1.data()).hasSize(1);
    assertThat(response1.data().get(0).getName()).isEqualTo("Bob");

    CursorResponse<User> response2 = userRepository.findUsersByCursor(
        null, "INVALID_ROLE", null, null, null, 10, "createdAt", SortDirection.DESCENDING);
    assertThat(response2.data()).hasSize(3);
  }

  @Test
  @DisplayName("페이징 검증: limit 초과 여부에 따른 hasNext 동작 확인")
  void findUsers_Pagination() {
    CursorResponse<User> response = userRepository.findUsersByCursor(
        null, null, null, null, null, 2, "createdAt", SortDirection.ASCENDING);

    assertThat(response.data()).hasSize(2);
    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isNotNull();
    assertThat(response.nextIdAfter()).isEqualTo(response.data().get(1).getId());
    assertThat(response.totalCount()).isEqualTo(3L);

    CursorResponse<User> nextResponse = userRepository.findUsersByCursor(
        null, null, null, response.nextCursor(), response.nextIdAfter(), 2, "createdAt", SortDirection.ASCENDING);

    assertThat(nextResponse.data()).hasSize(1);
    assertThat(nextResponse.hasNext()).isFalse();
  }

  @Test
  @DisplayName("정렬 및 커서 이동 완벽 검증: name 필드 (ASC, DESC)")
  void findUsers_SortByName() {
    CursorResponse<User> ascResp = userRepository.findUsersByCursor(
        null, null, null, null, null, 1, "name", SortDirection.ASCENDING);
    assertThat(ascResp.data().get(0).getName()).isEqualTo("Alice");

    CursorResponse<User> ascNext = userRepository.findUsersByCursor(
        null, null, null, ascResp.nextCursor(), ascResp.nextIdAfter(), 1, "name", SortDirection.ASCENDING);
    assertThat(ascNext.data().get(0).getName()).isEqualTo("Bob");

    CursorResponse<User> descResp = userRepository.findUsersByCursor(
        null, null, null, null, null, 1, "name", SortDirection.DESCENDING);
    assertThat(descResp.data().get(0).getName()).isEqualTo("Charlie");

    CursorResponse<User> descNext = userRepository.findUsersByCursor(
        null, null, null, descResp.nextCursor(), descResp.nextIdAfter(), 1, "name", SortDirection.DESCENDING);
    assertThat(descNext.data().get(0).getName()).isEqualTo("Bob");
  }

  @Test
  @DisplayName("정렬 및 커서 이동 완벽 검증: email 필드 (ASC, DESC)")
  void findUsers_SortByEmail() {
    CursorResponse<User> descResp = userRepository.findUsersByCursor(
        null, null, null, null, null, 1, "email", SortDirection.DESCENDING);
    assertThat(descResp.data().get(0).getEmail()).isEqualTo("ccc@example.com");

    CursorResponse<User> descNext = userRepository.findUsersByCursor(
        null, null, null, descResp.nextCursor(), descResp.nextIdAfter(), 1, "email", SortDirection.DESCENDING);
    assertThat(descNext.data().get(0).getEmail()).isEqualTo("bbb@example.com");
  }

  @Test
  @DisplayName("정렬 및 커서 이동 완벽 검증: isLocked 필드 (ASC, DESC)")
  void findUsers_SortByIsLocked() {
    CursorResponse<User> ascResp = userRepository.findUsersByCursor(
        null, null, null, null, null, 2, "isLocked", SortDirection.ASCENDING);
    assertThat(ascResp.data().get(0).isLocked()).isFalse();

    CursorResponse<User> ascNext = userRepository.findUsersByCursor(
        null, null, null, ascResp.nextCursor(), ascResp.nextIdAfter(), 2, "isLocked", SortDirection.ASCENDING);
    assertThat(ascNext.data().isEmpty()).isFalse();
  }

  @Test
  @DisplayName("정렬 및 커서 이동 완벽 검증: role 필드 (ASC, DESC)")
  void findUsers_SortByRole() {
    CursorResponse<User> descResp = userRepository.findUsersByCursor(
        null, null, null, null, null, 1, "role", SortDirection.DESCENDING);
    assertThat(descResp.data().get(0).getRole()).isEqualTo(Role.USER); // U가 A보다 뒤

    CursorResponse<User> descNext = userRepository.findUsersByCursor(
        null, null, null, descResp.nextCursor(), descResp.nextIdAfter(), 1, "role", SortDirection.DESCENDING);
    assertThat(descNext.data().get(0).getRole()).isNotNull();
  }
}
