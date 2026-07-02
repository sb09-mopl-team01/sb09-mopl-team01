package io.mopl.domain.playlist.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.playlist.dto.PlaylistDto;
import io.mopl.domain.playlist.dto.request.PlaylistCreateRequest;
import io.mopl.domain.playlist.dto.request.PlaylistUpdateRequest;
import io.mopl.domain.playlist.service.PlaylistService;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaylistControllerTest {

  @InjectMocks
  private PlaylistController playlistController;

  @Mock
  private PlaylistService playlistService;

  @Test
  @DisplayName("POST /api/playlists - 플레이리스트 생성 성공")
  void createPlaylist() {
    UUID userId = UUID.randomUUID();
    PlaylistCreateRequest request = new PlaylistCreateRequest("제목", "설명");

    ResponseEntity<Void> response = playlistController.createPlaylist(userId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(playlistService).createPlaylist(eq(userId), any());
  }

  @Test
  @DisplayName("PATCH /api/playlists/{id} - 수정 성공")
  void updatePlaylist() {
    UUID userId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");

    ResponseEntity<Void> response = playlistController.updatePlaylist(userId, playlistId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(playlistService).updatePlaylist(eq(userId), eq(playlistId), any());
  }

  @Test
  @DisplayName("DELETE /api/playlists/{id} - 삭제 성공")
  void deletePlaylist() {
    UUID userId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    ResponseEntity<Void> response = playlistController.deletePlaylist(userId, playlistId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(playlistService).deletePlaylist(userId, playlistId);
  }

  @Test
  @DisplayName("GET /api/playlists/{id} - 상세 조회 성공")
  void findPlaylist() {
    UUID userId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    PlaylistDto expectedDto = mock(PlaylistDto.class);
    given(playlistService.findPlaylist(eq(userId), eq(playlistId))).willReturn(expectedDto);

    ResponseEntity<PlaylistDto> response = playlistController.findPlaylist(userId, playlistId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedDto);
  }

  @Test
  @DisplayName("GET /api/playlists - 목록 조회 성공")
  void findPlaylists() {
    CursorResponse<PlaylistDto> fakeResponse = new CursorResponse<>(
        List.of(), null, null, false, 0L, "updatedAt", SortDirection.DESCENDING
    );
    given(playlistService.findPlaylists(
        any(), any(), any(), any(), any(), any(), eq(10), eq("DESCENDING"), eq("updatedAt")
    )).willReturn(fakeResponse);

    ResponseEntity<CursorResponse<PlaylistDto>> response = playlistController.findPlaylists(
        null, null, null, null, null, null, 10, "DESCENDING", "updatedAt"
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(fakeResponse);
  }

  @Test
  @DisplayName("POST /api/playlists/{id}/subscribe - 구독 성공")
  void subscribe() {
    UUID userId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    ResponseEntity<Void> response = playlistController.subscribePlaylist(userId, playlistId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(playlistService).subscribePlaylist(userId, playlistId);
  }
}