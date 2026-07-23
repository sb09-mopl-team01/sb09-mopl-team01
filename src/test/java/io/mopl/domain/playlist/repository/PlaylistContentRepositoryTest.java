package io.mopl.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.PlaylistContent;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.config.QueryDslConfig;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(QueryDslConfig.class)
class PlaylistContentRepositoryTest {

  @Autowired
  private PlaylistContentRepository playlistContentRepository;

  @Autowired
  private PlaylistRepository playlistRepository;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  void deleteAllByContentIdRemovesOnlyTargetContentLinks() {
    User owner = userRepository.save(User.builder()
        .email("playlist-owner@test.com")
        .passwordHash("password123")
        .name("Playlist Owner")
        .role(Role.USER)
        .build());
    Playlist playlist = playlistRepository.save(Playlist.create(owner, "Playlist", "Description"));
    Content target = contentRepository.save(Content.createManual(
        ContentType.MOVIE,
        "Target",
        "Target description",
        null,
        Set.of("영화")
    ));
    Content remaining = contentRepository.save(Content.createManual(
        ContentType.MOVIE,
        "Remaining",
        "Remaining description",
        null,
        Set.of("영화")
    ));
    playlistContentRepository.save(new PlaylistContent(playlist, target));
    PlaylistContent remainingLink = playlistContentRepository.save(
        new PlaylistContent(playlist, remaining)
    );

    int deletedCount = playlistContentRepository.deleteAllByContentId(target.getId());

    assertThat(deletedCount).isEqualTo(1);
    assertThat(playlistContentRepository.findAll())
        .extracting(PlaylistContent::getId)
        .containsExactly(remainingLink.getId());
  }
}
