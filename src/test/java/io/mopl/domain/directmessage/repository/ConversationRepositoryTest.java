package io.mopl.domain.directmessage.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.directmessage.entity.Conversation;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({
    io.mopl.global.config.AppConfig.class,
    io.mopl.global.config.QueryDslConfig.class
})
@ActiveProfiles("test")
class ConversationRepositoryTest {

  @Autowired
  private ConversationRepository conversationRepository;

  @Test
  void existsByIdAndParticipantIdReturnsTrueForConversationParticipants() {
    UUID firstUserId = UUID.randomUUID();
    UUID secondUserId = UUID.randomUUID();
    Conversation conversation = conversationRepository.saveAndFlush(
        Conversation.between(firstUserId, secondUserId)
    );

    assertThat(conversationRepository.existsByIdAndParticipantId(
        conversation.getId(),
        firstUserId
    )).isTrue();
    assertThat(conversationRepository.existsByIdAndParticipantId(
        conversation.getId(),
        secondUserId
    )).isTrue();
  }

  @Test
  void existsByIdAndParticipantIdReturnsFalseForNonParticipant() {
    Conversation conversation = conversationRepository.saveAndFlush(
        Conversation.between(UUID.randomUUID(), UUID.randomUUID())
    );

    assertThat(conversationRepository.existsByIdAndParticipantId(
        conversation.getId(),
        UUID.randomUUID()
    )).isFalse();
  }
}
