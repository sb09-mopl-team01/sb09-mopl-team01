package io.mopl.domain.auth.service;


import io.mopl.domain.auth.dto.TokenRefreshResult;
import io.mopl.domain.auth.repository.RefreshTokenMemoryRepository;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final JwtProvider jwtProvider;
  private final RefreshTokenMemoryRepository refreshTokenRepository;
  private final MoplUserDetailsService userDetailsService;
  private final UserMapper userMapper;

  public TokenRefreshResult refreshTokens(String currentRefreshToken) {
    if (currentRefreshToken == null || !jwtProvider.validateToken(currentRefreshToken)) {
      throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.");
    }

    String email = jwtProvider.getUsername(currentRefreshToken);
    if (!refreshTokenRepository.isValid(email, currentRefreshToken)) {
      throw new IllegalArgumentException("만료되었거나 조작된 리프레시 토큰입니다.");
    }

    refreshTokenRepository.removeToken(email, currentRefreshToken);

    MoplUserDetails userDetails = (MoplUserDetails) userDetailsService.loadUserByUsername(email);
    String newAccessToken = jwtProvider.generateAccessToken(userDetails);
    String newRefreshToken = jwtProvider.generateRefreshToken(email);

    refreshTokenRepository.save(email, newRefreshToken);

    UserDto userDto = userMapper.toDto(userDetails.getUser());
    return new TokenRefreshResult(newAccessToken, newRefreshToken, userDto);
  }

}
