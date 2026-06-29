package io.mopl.domain.review.replica.User;

import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Primary
@Repository("reviewReplicaUserRepository")
public interface UserRepository extends JpaRepository<User, UUID> {

}
