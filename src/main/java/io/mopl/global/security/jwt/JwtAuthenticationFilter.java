package io.mopl.global.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.global.security.MoplUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;
  private final MoplUserDetailsService userDetailsService;

  private final StringRedisTemplate redisTemplate;

  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");

    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);

      if (jwtProvider.validateToken(token)) {
        String email = jwtProvider.getUsername(token);
        UUID userId = jwtProvider.getUserId(token);

        boolean isBlacklisted = false;
        try {
          isBlacklisted = redisTemplate.hasKey("blacklist:access_token:" + userId);
        } catch (Exception e) {
          log.warn("JwtAuthenticationFilter Skipping token blacklist validation due to Redis connection error. userId={}", userId, e);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        if (isBlacklisted || !userDetails.isAccountNonLocked()) {
          sendAccountLockResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "계정이 잠금 처리되었습니다.");
          return;
        }

        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }

    filterChain.doFilter(request, response);
  }

  private void sendAccountLockResponse(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    Map<String, Object> errorResult = new HashMap<>();
    errorResult.put("status", status);
    errorResult.put("code", "ACCOUNT_LOCKED");
    errorResult.put("message", message);

    objectMapper.writeValue(response.getWriter(), errorResult);
  }
}
