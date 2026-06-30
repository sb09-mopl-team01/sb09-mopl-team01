package io.mopl.domain.user.repository;

import io.mopl.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);
}
