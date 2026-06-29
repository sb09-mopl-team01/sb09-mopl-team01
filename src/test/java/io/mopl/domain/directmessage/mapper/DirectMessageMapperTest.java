package io.mopl.domain.directmessage.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.directmessage.dto.DirectMessageDto;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.entity.DirectMessage;
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
@Import(DirectMessageMapper.class)
class DirectMessageMapperTest {

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private DirectMessageMapper directMessageMapper;

  @Test
  void toDtoMapsDirectMessageSchemaFields() {
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    Conversation conversation = Conversation.between(senderId, receiverId);
    entityManager.persist(conversation);

    DirectMessage directMessage = DirectMessage.create(
        conversation,
        senderId,
        receiverId,
        "hello"
    );
    entityManager.persist(directMessage);
    entityManager.flush();

    DirectMessageDto result = directMessageMapper.toDto(directMessage);

    assertThat(result.id()).isEqualTo(directMessage.getId());
    assertThat(result.conversationId()).isEqualTo(conversation.getId());
    assertThat(result.createdAt()).isEqualTo(directMessage.getCreatedAt());
    assertThat(result.sender().userId()).isEqualTo(senderId);
    assertThat(result.receiver().userId()).isEqualTo(receiverId);
    assertThat(result.content()).isEqualTo("hello");
  }
}
