package io.mopl.global.websocket;

import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtProvider jwtProvider;
  private final MoplUserDetailsService userDetailsService;
  private final StringRedisTemplate redisTemplate;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      String token = resolveToken(accessor);
      if (!StringUtils.hasText(token) || !jwtProvider.validateToken(token)) {
        throw new AuthenticationCredentialsNotFoundException("WebSocket 인증 정보가 유효하지 않습니다.");
      }

      String email = jwtProvider.getUsername(token);
      UUID userId = jwtProvider.getUserId(token);
      UserDetails userDetails = userDetailsService.loadUserByUsername(email);
      if (isBlacklisted(userId) || !userDetails.isAccountNonLocked()) {
        throw new AuthenticationCredentialsNotFoundException("WebSocket 인증 정보가 유효하지 않습니다.");
      }

      Authentication authentication = new UsernamePasswordAuthenticationToken(
          userDetails,
          null,
          userDetails.getAuthorities()
      );
      accessor.setUser(authentication);
    }

    return message;
  }

  private boolean isBlacklisted(UUID userId) {
    try {
      return redisTemplate.hasKey("blacklist:access_token:" + userId);
    } catch (Exception e) {
      log.warn(
          "WebSocketJwtChannelInterceptor Skipping token blacklist validation due to Redis connection error. userId={}",
          userId,
          e
      );
      return false;
    }
  }

  private String resolveToken(StompHeaderAccessor accessor) {
    String authorization = firstHeader(accessor, "Authorization");
    if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
      return authorization.substring(BEARER_PREFIX.length());
    }

    return firstHeader(accessor, "accessToken");
  }

  private String firstHeader(StompHeaderAccessor accessor, String name) {
    List<String> values = accessor.getNativeHeader(name);
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.get(0);
  }
}
