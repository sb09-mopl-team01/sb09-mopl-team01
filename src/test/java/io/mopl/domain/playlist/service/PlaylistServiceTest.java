package io.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.mopl.domain.playlist.dto.request.PlaylistCreateRequest;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.repository.PlaylistRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

  @Mock private PlaylistRepository playlistRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks
  private PlaylistService playlistService;

  @Test
  @DisplayName("플레이리스트 생성 성공")
  void createPlaylist_Success() {

    UUID userId = UUID.randomUUID();
    User owner = User.builder().name("Test User").build();
    ReflectionTestUtils.setField(owner, "id", userId);

    PlaylistCreateRequest request = new PlaylistCreateRequest("Test Title", "Test Description");

    given(userRepository.findById(userId)).willReturn(Optional.of(owner));

    playlistService.createPlaylist(userId, request);

    verify(playlistRepository).save(any(Playlist.class));
  }

  @Test
  @DisplayName("권한 없는 플레이리스트 삭제 시도 시 FORBIDDEN 예외 발생")
  void deletePlaylist_Forbidden() {

    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", ownerId);

    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));

    assertThatThrownBy(() -> playlistService.deletePlaylist(requesterId, playlistId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("본인의 플레이리스트 구독 시도 시 INVALID_INPUT 예외 발생")
  void subscribePlaylist_SelfSubscribe_Fail() {
    UUID userId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = User.builder().name("Owner").build();
    ReflectionTestUtils.setField(owner, "id", userId);

    Playlist playlist = Playlist.create(owner, "Title", "Desc");
    ReflectionTestUtils.setField(playlist, "id", playlistId);

    given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
    given(userRepository.findById(userId)).willReturn(Optional.of(owner));

    assertThatThrownBy(() -> playlistService.subscribePlaylist(userId, playlistId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
  }
}