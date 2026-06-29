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

}
