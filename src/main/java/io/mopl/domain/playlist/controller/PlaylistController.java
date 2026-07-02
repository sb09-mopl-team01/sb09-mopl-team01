package io.mopl.domain.playlist.controller;

import io.mopl.domain.playlist.dto.PlaylistDto;
import io.mopl.domain.playlist.dto.request.PlaylistCreateRequest;
import io.mopl.domain.playlist.dto.request.PlaylistUpdateRequest;
import io.mopl.domain.playlist.service.PlaylistService;
import io.mopl.global.response.CursorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {

  private final PlaylistService playlistService;

  @PostMapping
  public ResponseEntity<Void> createPlaylist(
      @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody PlaylistCreateRequest request) {

    playlistService.createPlaylist(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @DeleteMapping("/{playlistId}")
  public ResponseEntity<Void> deletePlaylist(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID playlistId) {

    playlistService.deletePlaylist(userId, playlistId);
    return ResponseEntity.ok().build();
  }

  @PatchMapping("/{playlistId}")
  public ResponseEntity<Void> updatePlaylist(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID playlistId,
      @Valid @RequestBody PlaylistUpdateRequest request) {

    playlistService.updatePlaylist(userId, playlistId, request);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> subscribePlaylist(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID playlistId) {

    playlistService.subscribePlaylist(userId, playlistId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @DeleteMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> unsubscribePlaylist(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID playlistId) {

    playlistService.unsubscribePlaylist(userId, playlistId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @PostMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> addContentToPlaylist(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId) {

    playlistService.addContentToPlaylist(userId, playlistId, contentId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @DeleteMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> removeContentFromPlaylist(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId) {

    playlistService.removeContentFromPlaylist(userId, playlistId, contentId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @GetMapping("/{playlistId}")
  public ResponseEntity<PlaylistDto> findPlaylist(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID playlistId) {

    PlaylistDto response = playlistService.findPlaylist(userId, playlistId);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<CursorResponse<PlaylistDto>> findPlaylists(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(required = false) String keywordLike,
      @RequestParam(required = false) UUID ownerIdEqual,
      @RequestParam(required = false) UUID subscriberIdEqual,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      @RequestParam String sortDirection,
      @RequestParam String sortBy) {

    CursorResponse<PlaylistDto> response = playlistService.findPlaylists(
        userId, keywordLike, ownerIdEqual, subscriberIdEqual,
        cursor, idAfter, limit, sortDirection, sortBy
    );
    return ResponseEntity.ok(response);
  }
}