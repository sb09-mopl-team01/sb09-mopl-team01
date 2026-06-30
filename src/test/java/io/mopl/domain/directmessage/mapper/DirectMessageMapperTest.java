package io.mopl.domain.directmessage.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.entity.DirectMessage;
import io.mopl.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@ActiveProfiles("test")
@Import({DirectMessageMapper.class, QueryDslConfig.class})
class DirectMessageMapperTest {

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
