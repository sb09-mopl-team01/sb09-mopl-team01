package io.mopl.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.mopl.domain.user.entity.User;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;

class WebSocketJwtChannelInterceptorTest {

  private final JwtProvider jwtProvider = mock(JwtProvider.class);
  private final MoplUserDetailsService userDetailsService = mock(MoplUserDetailsService.class);
  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  private final WebSocketJwtChannelInterceptor interceptor =
      new WebSocketJwtChannelInterceptor(jwtProvider, userDetailsService, redisTemplate);

  @Test
  @DisplayName("CONNECT 요청에 유효한 JWT가 있으면 Principal을 설정한다")
  void preSendWithValidToken() {
    String token = "valid-token";
    String email = "user@example.com";
    UUID userId = UUID.randomUUID();
    MoplUserDetails userDetails = new MoplUserDetails(user(email, false));
    given(jwtProvider.validateToken(token)).willReturn(true);
    given(jwtProvider.getUsername(token)).willReturn(email);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(redisTemplate.hasKey("blacklist:access_token:" + userId)).willReturn(false);
    given(userDetailsService.loadUserByUsername(email)).willReturn(userDetails);

    Message<?> result = interceptor.preSend(connectMessage("Bearer " + token), null);

    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
    assertThat(accessor).isNotNull();
    assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
    Authentication authentication = (Authentication) accessor.getUser();
    assertThat(authentication.getPrincipal()).isEqualTo(userDetails);
  }

  @Test
  @DisplayName("CONNECT 요청에 JWT가 없으면 연결을 거부한다")
  void preSendWithoutToken() {
    assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  @DisplayName("CONNECT 요청의 JWT가 유효하지 않으면 연결을 거부한다")
  void preSendWithInvalidToken() {
    String token = "invalid-token";
    given(jwtProvider.validateToken(token)).willReturn(false);

    assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer " + token), null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  @DisplayName("CONNECT 요청의 JWT가 블랙리스트에 있으면 연결을 거부한다")
  void preSendWithBlacklistedToken() {
    String token = "blacklisted-token";
    String email = "user@example.com";
    UUID userId = UUID.randomUUID();
    given(jwtProvider.validateToken(token)).willReturn(true);
    given(jwtProvider.getUsername(token)).willReturn(email);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(userDetailsService.loadUserByUsername(email)).willReturn(new MoplUserDetails(user(email, false)));
    given(redisTemplate.hasKey("blacklist:access_token:" + userId)).willReturn(true);

    assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer " + token), null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  @DisplayName("CONNECT 요청의 사용자가 잠금 상태이면 연결을 거부한다")
  void preSendWithLockedUser() {
    String token = "locked-user-token";
    String email = "user@example.com";
    UUID userId = UUID.randomUUID();
    given(jwtProvider.validateToken(token)).willReturn(true);
    given(jwtProvider.getUsername(token)).willReturn(email);
    given(jwtProvider.getUserId(token)).willReturn(userId);
    given(userDetailsService.loadUserByUsername(email)).willReturn(new MoplUserDetails(user(email, true)));
    given(redisTemplate.hasKey("blacklist:access_token:" + userId)).willReturn(false);

    assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer " + token), null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  private Message<byte[]> connectMessage(String authorizationHeader) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    if (authorizationHeader != null) {
      accessor.setNativeHeader("Authorization", authorizationHeader);
    }
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private User user(String email, boolean locked) {
    User user = User.builder()
        .email(email)
        .passwordHash("hash")
        .name("사용자")
        .build();
    if (locked) {
      user.lockAccount();
    }
    return user;
  }
}
