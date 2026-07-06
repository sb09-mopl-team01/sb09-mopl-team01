package io.mopl.domain.user.service;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.user.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class UserServiceCacheTest {

  @Autowired
  private UserService userService;

  @MockitoBean
  private UserRepository userRepository;

  @Test
  @DisplayName("캐시 테스트: 같은 유저를 2번 조회하면 DB 조회는 1번만 발생")
  void findUser_CacheHit_Test() {
    UUID userId = UUID.randomUUID();
    User mockUser = User.builder().email("test@example.com").build();
    given(userRepository.findById(userId)).willReturn(Optional.of(mockUser));

    System.out.println("=== 첫 번째 조회 (캐시 Miss 예상) ===");
    userService.findUser(userId);

    System.out.println("=== 두 번째 조회 (캐시 Hit 예상) ===");
    userService.findUser(userId);

    verify(userRepository, times(1)).findById(userId);
  }
}