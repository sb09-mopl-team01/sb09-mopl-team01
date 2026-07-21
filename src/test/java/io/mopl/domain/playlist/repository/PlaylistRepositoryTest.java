package io.mopl.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.config.QueryDslConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(QueryDslConfig.class)
class PlaylistRepositoryTest {

  @Autowired private PlaylistRepository playlistRepository;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("키워드로 플레이리스트 조회 성공 (실제 쿼리 검증)")
  void findPlaylistsByCursor_Integration() {
    User user = User.builder()
        .email("test@test.com")
        .passwordHash("password123")
        .name("Tester")
        .profileImageUrl(null)
        .role(Role.USER)
        .build();
    userRepository.save(user);

    Playlist p1 = Playlist.create(user, "Summer Vibe", "Hot songs");
    playlistRepository.save(p1);

    List<Playlist> results = playlistRepository.findPlaylistsByCursor(
        "Summer", null, null, null, null, 10, "DESCENDING", "updatedAt"
    );

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getTitle()).isEqualTo("Summer Vibe");
  }
}