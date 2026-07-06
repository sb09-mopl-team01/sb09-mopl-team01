package io.mopl.domain.user.service;

import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${mopl.admin.username}") private String adminUsername;
  @Value("${mopl.admin.password}") private String adminPassword;
  @Value("${mopl.admin.email}") private String adminEmail;

  @Transactional
  @Override
  public void run(String... args) throws Exception {

    if (userRepository.findByEmail(adminEmail).isEmpty()) {

      User admin = User.builder()
          .name(adminUsername)
          .email(adminEmail)
          .passwordHash(passwordEncoder.encode(adminPassword))
          .role(Role.ADMIN)
          .build();

      userRepository.save(admin);
      log.info("AdminAccount Initialize Completed. email={}", adminEmail);
    } else {
      log.info("AdminAccount Initialize Skipped. Admin account already exists. email={}", adminEmail);
    }
  }
}
