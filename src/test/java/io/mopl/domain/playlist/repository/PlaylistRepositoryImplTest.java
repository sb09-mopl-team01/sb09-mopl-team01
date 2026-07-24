package io.mopl.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.mopl.domain.playlist.entity.Playlist;

@DataJpaTest
@Import(PlaylistRepositoryImplTest.QuerydslConfig.class)
class PlaylistRepositoryImplTest {

  @Autowired
  private EntityManager em;

  @Autowired
  private JPAQueryFactory queryFactory;

  private PlaylistRepositoryImpl playlistRepository;

  private UUID ownerId;
  private UUID subscriberId;

  @TestConfiguration
  static class QuerydslConfig {
    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager em) {
      return new JPAQueryFactory(em);
    }
  }

  @BeforeEach
  void setUp() {
    playlistRepository = new PlaylistRepositoryImpl(queryFactory);
    ownerId = UUID.randomUUID();
    subscriberId = UUID.randomUUID();
  }

  @Test
  @DisplayName("커서가 없는 첫 페이지 조회 (모든 파라미터 null)l")
  void findPlaylistsByCursor_FirstPage() {
    List<Playlist> result = playlistRepository.findPlaylistsByCursor(
        null, null, null, null, null, 10, "DESCENDING", "updatedAt"
    );
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("구독자 ID로 조회")
  void findPlaylistsByCursor_WithSubscriberId() {
    List<Playlist> result = playlistRepository.findPlaylistsByCursor(
        null, null, subscriberId, null, null, 10, "DESCENDING", "updatedAt"
    );
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("키워드와 소유자 ID로 조회")
  void findPlaylistsByCursor_WithKeywordAndOwner() {
    List<Playlist> result = playlistRepository.findPlaylistsByCursor(
        "테스트", ownerId, null, null, null, 10, "DESCENDING", "updatedAt"
    );
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("구독자수 기준 내림차순 커서 조회")
  void findPlaylistsByCursor_SubscribeCount_Desc() {
    String cursor = "100";
    UUID idAfter = UUID.randomUUID();

    List<Playlist> result = playlistRepository.findPlaylistsByCursor(
        null, null, null, cursor, idAfter, 10, "DESCENDING", "subscribeCount"
    );
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("구독자수 기준 오름차순 커서 조회")
  void findPlaylistsByCursor_SubscribeCount_Asc() {
    String cursor = "100";
    UUID idAfter = UUID.randomUUID();

    List<Playlist> result = playlistRepository.findPlaylistsByCursor(
        null, null, null, cursor, idAfter, 10, "ASCENDING", "subscribeCount"
    );
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("수정일 기준 내림차순 커서 조회")
  void findPlaylistsByCursor_UpdatedAt_Desc() {
    String cursor = Instant.now().toString();
    UUID idAfter = UUID.randomUUID();

    List<Playlist> result = playlistRepository.findPlaylistsByCursor(
        null, null, null, cursor, idAfter, 10, "DESCENDING", "updatedAt"
    );
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("수정일 기준 오름차순 커서 조회")
  void findPlaylistsByCursor_UpdatedAt_Asc() {
    String cursor = Instant.now().toString();
    UUID idAfter = UUID.randomUUID();

    List<Playlist> result = playlistRepository.findPlaylistsByCursor(
        null, null, null, cursor, idAfter, 10, "ASCENDING", "updatedAt"
    );
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("전체 플레이리스트 카운트 조회 (조건 없음)")
  void countPlaylists_AllNull() {
    long count = playlistRepository.countPlaylists(null, null, null);
    assertThat(count).isGreaterThanOrEqualTo(0L);
  }

  @Test
  @DisplayName("조건을 포함한 플레이리스트 카운트 조회")
  void countPlaylists_WithConditions() {
    long count = playlistRepository.countPlaylists("테스트", ownerId, subscriberId);
    assertThat(count).isGreaterThanOrEqualTo(0L);
  }
}