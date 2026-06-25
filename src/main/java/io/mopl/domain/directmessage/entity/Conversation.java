package io.mopl.domain.directmessage.entity;

import io.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "conversations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_conversation_participants",
            columnNames = {"participant_a_id", "participant_b_id"}
        )
    }
)
public class Conversation extends BaseEntity {

  @Column(name = "participant_a_id", nullable = false)
  private UUID participantAId;

  @Column(name = "participant_b_id", nullable = false)
  private UUID participantBId;

  private Conversation(UUID participantAId, UUID participantBId) {
    this.participantAId = participantAId;
    this.participantBId = participantBId;
  }

  public static Conversation between(UUID firstUserId, UUID secondUserId) {
    if (firstUserId.compareTo(secondUserId) <= 0) {
      return new Conversation(firstUserId, secondUserId);
    }
    return new Conversation(secondUserId, firstUserId);
  }

  public UUID getOtherParticipantId(UUID userId) {
    if (participantAId.equals(userId)) {
      return participantBId;
    }
    return participantAId;
  }
}
