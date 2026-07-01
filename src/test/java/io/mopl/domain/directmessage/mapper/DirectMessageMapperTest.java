package io.mopl.domain.directmessage.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.entity.DirectMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DirectMessageMapperTest {

  private final DirectMessageMapper directMessageMapper = new DirectMessageMapper();

  @Test
  void toDtoMapsDirectMessageSchemaFields() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    Conversation conversation = Conversation.between(senderId, receiverId);
    UUID conversationId = UUID.randomUUID();
    ReflectionTestUtils.setField(conversation, "id", conversationId);

    DirectMessage directMessage = DirectMessage.create(
        conversation,
        senderId,
        receiverId,
        "hello"
    );
    UUID directMessageId = UUID.randomUUID();
    Instant createdAt = Instant.now();
    ReflectionTestUtils.setField(directMessage, "id", directMessageId);
    ReflectionTestUtils.setField(directMessage, "createdAt", createdAt);

    DirectMessageDto result = directMessageMapper.toDto(directMessage);

    assertThat(result.id()).isEqualTo(directMessageId);
    assertThat(result.conversationId()).isEqualTo(conversationId);
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.sender().userId()).isEqualTo(senderId);
    assertThat(result.receiver().userId()).isEqualTo(receiverId);
    assertThat(result.content()).isEqualTo("hello");
  }
}
