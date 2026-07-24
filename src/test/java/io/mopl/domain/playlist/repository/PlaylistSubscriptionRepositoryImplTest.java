package io.mopl.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
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
@Import(PlaylistSubscriptionRepositoryImplTest.QuerydslConfig.class)
class PlaylistSubscriptionRepositoryImplTest {

  @Autowired
  private EntityManager em;

  @Autowired
  private JPAQueryFactory queryFactory;

  private PlaylistSubscriptionRepositoryImpl playlistSubscriptionRepository;

  @TestConfiguration
  static class QuerydslConfig {
    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager em) {
      return new JPAQueryFactory(em);
    }
  }

  @BeforeEach
  void setUp() {
    playlistSubscriptionRepository = new PlaylistSubscriptionRepositoryImpl(queryFactory);
  }

  @Test
  @DisplayName("특정 플레이리스트를 구독한 유저 ID 목록을 정상적으로 반환한다")
  void findSubscriberIdsByPlaylistId_Success() {

    UUID playlistId = UUID.randomUUID();

    List<UUID> subscriberIds = playlistSubscriptionRepository.findSubscriberIdsByPlaylistId(playlistId);

    assertThat(subscriberIds).isNotNull();
  }

  @Test
  @DisplayName("구독자가 없는 플레이리스트를 조회하면 빈 리스트를 반환한다")
  void findSubscriberIdsByPlaylistId_Empty() {

    UUID notSubscribedPlaylistId = UUID.randomUUID();

    List<UUID> subscriberIds = playlistSubscriptionRepository.findSubscriberIdsByPlaylistId(notSubscribedPlaylistId);

    assertThat(subscriberIds).isNotNull();
    assertThat(subscriberIds).isEmpty();
  }
}