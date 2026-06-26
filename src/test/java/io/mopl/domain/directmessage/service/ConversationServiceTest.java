package io.mopl.domain.directmessage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mopl.domain.directmessage.dto.ConversationCreateRequest;
import io.mopl.domain.directmessage.dto.ConversationDto;
import io.mopl.domain.directmessage.mapper.ConversationMapper;
import io.mopl.domain.directmessage.repository.ConversationRepository;
import io.mopl.global.exception.BaseException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({ConversationService.class, ConversationMapper.class})
class ConversationServiceTest {

  @Autowired
  private ConversationService conversationService;

  @Autowired
  private ConversationRepository conversationRepository;

  @Test
  void createConversationCreatesNewConversation() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();

    ConversationDto result = conversationService.createConversation(
        requesterId,
        new ConversationCreateRequest(withUserId)
    );

    assertThat(result.id()).isNotNull();
    assertThat(result.with().userId()).isEqualTo(withUserId);
    assertThat(result.latestMessage()).isNull();
    assertThat(result.hasUnread()).isFalse();
    assertThat(conversationRepository.count()).isEqualTo(1);
  }

  @Test
  void createConversationReturnsExistingConversationForSameParticipants() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();

    ConversationDto first = conversationService.createConversation(
        requesterId,
        new ConversationCreateRequest(withUserId)
    );
    ConversationDto second = conversationService.createConversation(
        withUserId,
        new ConversationCreateRequest(requesterId)
    );

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(conversationRepository.count()).isEqualTo(1);
  }

  @Test
  void createConversationRejectsSelfConversation() {
    UUID requesterId = UUID.randomUUID();

    assertThatThrownBy(() -> conversationService.createConversation(
        requesterId,
        new ConversationCreateRequest(requesterId)
    )).isInstanceOf(BaseException.class);
  }
}
