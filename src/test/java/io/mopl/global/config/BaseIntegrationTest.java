package io.mopl.global.config;

import io.mopl.domain.user.repository.search.UserSearchRepository;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
public abstract class BaseIntegrationTest {

  @MockitoBean
  protected UserSearchRepository userSearchRepository;

  @MockitoBean
  protected RedissonClient redissonClient;

}
