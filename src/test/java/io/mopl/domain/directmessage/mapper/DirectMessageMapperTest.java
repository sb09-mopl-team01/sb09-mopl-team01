package io.mopl.domain.directmessage.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.entity.DirectMessage;
import io.mopl.domain.user.entity.User;
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

  @Test
  void toDtoMapsUserSummaryFromUsers() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    User sender = createUser(senderId, "sender");
    User receiver = createUser(receiverId, "receiver");
    Conversation conversation = Conversation.between(senderId, receiverId);
    ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
    DirectMessage directMessage = DirectMessage.create(conversation, senderId, receiverId, "hello");
    ReflectionTestUtils.setField(directMessage, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(directMessage, "createdAt", Instant.now());

    DirectMessageDto result = directMessageMapper.toDto(directMessage, sender, receiver);

    assertThat(result.sender().userId()).isEqualTo(senderId);
    assertThat(result.sender().name()).isEqualTo("sender");
    assertThat(result.receiver().userId()).isEqualTo(receiverId);
    assertThat(result.receiver().name()).isEqualTo("receiver");
  }

  private User createUser(UUID id, String name) {
    User user = User.builder()
        .email(name + "@example.com")
        .passwordHash("password")
        .name(name)
        .build();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
