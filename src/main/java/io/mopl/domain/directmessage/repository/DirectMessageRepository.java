package io.mopl.domain.directmessage.repository;

import io.mopl.domain.directmessage.entity.DirectMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID>, DirectMessageRepositoryCustom {
}
