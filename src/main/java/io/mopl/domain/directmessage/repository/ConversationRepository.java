package io.mopl.domain.directmessage.repository;

import io.mopl.domain.directmessage.entity.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  Optional<Conversation> findByParticipantAIdAndParticipantBId(UUID participantAId, UUID participantBId);
}
