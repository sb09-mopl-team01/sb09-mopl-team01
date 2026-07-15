package io.mopl.domain.playlist.controller;

import io.mopl.domain.playlist.dto.PlaylistDto;
import io.mopl.domain.playlist.dto.request.PlaylistCreateRequest;
import io.mopl.domain.playlist.dto.request.PlaylistUpdateRequest;
import io.mopl.domain.playlist.service.PlaylistService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.security.MoplUserDetails;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {

  private final PlaylistService playlistService;

  @PostMapping
  @CrossOrigin(exposedHeaders = "Location")
  public ResponseEntity<PlaylistDto> createPlaylist(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @Valid @RequestBody PlaylistCreateRequest request) {

    PlaylistDto createdPlaylist = playlistService.createPlaylist(userDetails.getUser().getId(), request);

    return ResponseEntity.created(java.net.URI.create("/api/playlists/" + createdPlaylist.id()))
        .body(createdPlaylist);
  }

  @PatchMapping("/{playlistId}")
  public ResponseEntity<Void> updatePlaylist(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @PathVariable UUID playlistId,
      @Valid @RequestBody PlaylistUpdateRequest request) {

    playlistService.updatePlaylist(userDetails.getUser().getId(), playlistId, request);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{playlistId}")
  public ResponseEntity<Void> deletePlaylist(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @PathVariable UUID playlistId) {

    playlistService.deletePlaylist(userDetails.getUser().getId(), playlistId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> subscribePlaylist(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @PathVariable UUID playlistId) {

    playlistService.subscribePlaylist(userDetails.getUser().getId(), playlistId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @DeleteMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> unsubscribePlaylist(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @PathVariable UUID playlistId) {

    playlistService.unsubscribePlaylist(userDetails.getUser().getId(), playlistId);

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @PostMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> addContentToPlaylist(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId) {

    playlistService.addContentToPlaylist(userDetails.getUser().getId(), playlistId, contentId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }


  @DeleteMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> removeContentFromPlaylist(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId) {

    playlistService.removeContentFromPlaylist(userDetails.getUser().getId(), playlistId, contentId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @GetMapping("/{playlistId}")
  public ResponseEntity<PlaylistDto> findPlaylist(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @PathVariable UUID playlistId) {

    PlaylistDto response = playlistService.findPlaylist(userDetails.getUser().getId(), playlistId);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<CursorResponse<PlaylistDto>> findPlaylists(
      @AuthenticationPrincipal MoplUserDetails userDetails,
      @RequestParam(required = false) String keywordLike,
      @RequestParam(required = false) UUID ownerIdEqual,
      @RequestParam(required = false) UUID subscriberIdEqual,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      @RequestParam String sortDirection,
      @RequestParam String sortBy) {

    CursorResponse<PlaylistDto> response = playlistService.findPlaylists(
        userDetails.getUser().getId(), keywordLike, ownerIdEqual, subscriberIdEqual,
        cursor, idAfter, limit, sortDirection, sortBy
    );
    return ResponseEntity.ok(response);
  }
}