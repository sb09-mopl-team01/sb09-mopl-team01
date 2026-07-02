package io.mopl.domain.follow.controller;

import io.mopl.domain.follow.dto.FollowCreateRequest;
import io.mopl.domain.follow.dto.FollowDto;
import io.mopl.domain.follow.service.FollowService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

  private final FollowService followService;

  @PostMapping
  public ResponseEntity<FollowDto> follow(
      @AuthenticationPrincipal(expression = "user.id") UUID followerId,
      @Valid @RequestBody FollowCreateRequest request
  ) {
    FollowDto response = followService.follow(followerId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> unfollow(
      @AuthenticationPrincipal(expression = "user.id") UUID followerId,
      @PathVariable UUID followId
  ) {
    followService.unfollow(followerId, followId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/followed-by-me")
  public ResponseEntity<FollowDto> findFollowedByMe(
      @AuthenticationPrincipal(expression = "user.id") UUID followerId,
      @RequestParam UUID followeeId
  ) {
    FollowDto response = followService.findFollowedByMe(followerId, followeeId);
    return ResponseEntity.ok(response);
  }
}
