package io.mopl.domain.auth.service;

import io.mopl.domain.auth.dto.LoginResponse;
import io.mopl.domain.auth.exception.InvalidEmailException;
import io.mopl.domain.auth.exception.InvalidPasswordException;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;

  public LoginResponse login(String email, String rawPassword) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(InvalidEmailException::new);

    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new InvalidPasswordException();
    }

    MoplUserDetails userDetails = new MoplUserDetails(user);
    String accessToken = jwtProvider.generateAccessToken(userDetails);

    UserDto userDto = userMapper.toDto(user);
    return new LoginResponse(userDto, accessToken);
  }
}
