package io.mopl.domain.watchingsession.websocket;

import io.mopl.global.security.MoplUserDetails;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class WatchingSessionSubscriptionResolver {

  private static final Pattern WATCH_SUBSCRIPTION_PATTERN =
      Pattern.compile("^/sub/contents/([^/]+)/watch$");

  public Optional<UUID> resolveContentId(String destination) {
    Matcher matcher = WATCH_SUBSCRIPTION_PATTERN.matcher(destination == null ? "" : destination);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(UUID.fromString(matcher.group(1)));
  }

  public UUID resolveWatcherId(Principal principal) {
    if (principal instanceof Authentication authentication
        && authentication.getPrincipal() instanceof MoplUserDetails userDetails
        && userDetails.getUser() != null
        && userDetails.getUser().getId() != null) {
      return userDetails.getUser().getId();
    }

    throw new AuthenticationCredentialsNotFoundException("WebSocket 인증 정보가 필요합니다.");
  }
}
