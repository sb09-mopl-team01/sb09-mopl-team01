package io.mopl.domain.directmessage.entity;

import io.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "direct_messages",
    indexes = {
        @Index(
            name = "idx_direct_messages_conversation_created_at_id",
            columnList = "conversation_id, created_at, id"
        ),
        @Index(
            name = "idx_direct_messages_receiver_read_created_at_id",
            columnList = "receiver_id, read, created_at, id"
        )
    }
)
public class DirectMessage extends BaseEntity {

  public static final int CONTENT_MAX_LENGTH = 1000;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @Column(name = "sender_id", nullable = false)
  private UUID senderId;

  @Column(name = "receiver_id", nullable = false)
  private UUID receiverId;

  @Column(nullable = false, length = CONTENT_MAX_LENGTH)
  private String content;

  @Column(nullable = false)
  private boolean read = false;

  private DirectMessage(
      Conversation conversation,
      UUID senderId,
      UUID receiverId,
      String content
  ) {
    this.conversation = conversation;
    this.senderId = senderId;
    this.receiverId = receiverId;
    this.content = content;
  }

  public static DirectMessage create(
      Conversation conversation,
      UUID senderId,
      UUID receiverId,
      String content
  ) {
    return new DirectMessage(conversation, senderId, receiverId, content);
  }

  public void markAsRead() {
    this.read = true;
  }
}
