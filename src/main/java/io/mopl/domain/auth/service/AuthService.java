package io.mopl.domain.auth.service;

import io.mopl.domain.auth.dto.TokenRefreshResult;
import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.mail.event.TempPasswordIssuedEvent;
import io.mopl.domain.mail.service.MailService;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import javax.security.auth.login.AccountLockedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final JwtProvider jwtProvider;
  private final RefreshTokenRepository refreshTokenRepository;
  private final MoplUserDetailsService userDetailsService;
  private final UserMapper userMapper;
  private final UserRepository userRepository;
  private final TempPasswordService tempPasswordService;
  private final PasswordEncoder passwordEncoder;
  private final DomainEventPublisher eventPublisher;

  public TokenRefreshResult refreshTokens(String currentRefreshToken) {
    log.debug("Auth Token-Refresh Started.");

    if (currentRefreshToken == null || !jwtProvider.validateToken(currentRefreshToken)) {
      log.warn("Auth Token-Refresh Failed. Invalid refresh token.");
      throw new BaseException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    String email = jwtProvider.getUsername(currentRefreshToken);
    if (!refreshTokenRepository.isValid(email, currentRefreshToken)) {
      log.warn("Auth Token-Refresh Failed. Expired or manipulated token. email={}", email);
      throw new BaseException(ErrorCode.EXPIRED_OR_MANIPULATED_REFRESH_TOKEN);
    }

    refreshTokenRepository.removeToken(email, currentRefreshToken);

    MoplUserDetails userDetails = (MoplUserDetails) userDetailsService.loadUserByUsername(email);

    if (!userDetails.isAccountNonLocked()) {
      log.warn("Auth Token-Refresh Failed. Account is locked. email={}", email);

      refreshTokenRepository.removeToken(email, currentRefreshToken);

      throw new BaseException(ErrorCode.ACCOUNT_LOCKED);
    }

    String newAccessToken = jwtProvider.generateAccessToken(userDetails);
    String newRefreshToken = jwtProvider.generateRefreshToken(email);

    refreshTokenRepository.save(email, newRefreshToken);

    UserDto userDto = userMapper.toDto(userDetails.getUser());

    log.debug("Auth Token Refresh Completed. email={}", email);
    return new TokenRefreshResult(newAccessToken, newRefreshToken, userDto);
  }

  public void resetPassword(String email) {
    userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);

    String tempPassword = tempPasswordService.generateRandomPassword();
    String encodedTempPassword = passwordEncoder.encode(tempPassword);

    tempPasswordService.saveTempPassword(email, encodedTempPassword);

    eventPublisher.publish(new TempPasswordIssuedEvent(email, tempPassword));

    log.debug("Auth Reset Password Completed. email={}", email);
  }
}
