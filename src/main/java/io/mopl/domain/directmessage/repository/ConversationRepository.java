package io.mopl.domain.directmessage.repository;

import io.mopl.domain.directmessage.entity.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID>, ConversationRepositoryCustom {

  Optional<Conversation> findByParticipantAIdAndParticipantBId(UUID participantAId, UUID participantBId);

  @Query("""
      select case when count(c) > 0 then true else false end
      from Conversation c
      where c.id = :conversationId
        and (c.participantAId = :participantId or c.participantBId = :participantId)
      """)
  boolean existsByIdAndParticipantId(
      @Param("conversationId") UUID conversationId,
      @Param("participantId") UUID participantId
  );
}
