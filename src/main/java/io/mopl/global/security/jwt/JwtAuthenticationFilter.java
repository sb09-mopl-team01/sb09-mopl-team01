package io.mopl.global.security.jwt;

import io.mopl.global.security.MoplUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;
  private final MoplUserDetailsService userDetailsService;

  private final StringRedisTemplate redisTemplate;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");

    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);

      if (jwtProvider.validateToken(token)) {
        String email = jwtProvider.getUsername(token);

        if (redisTemplate.hasKey("locked:user:" + email)) {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "이 계정은 잠금 처리되어 즉시 로그아웃 되었습니다.");
          return;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!userDetails.isAccountNonLocked()) {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "계정이 잠금 처리되었습니다.");
          return;
        }

        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }

    filterChain.doFilter(request, response);
  }
}
