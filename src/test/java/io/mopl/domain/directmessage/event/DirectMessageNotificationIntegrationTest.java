package io.mopl.domain.directmessage.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.directmessage.dto.DirectMessageSendRequest;
import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.domain.directmessage.repository.ConversationRepository;
import io.mopl.domain.directmessage.service.ConversationService;
import io.mopl.domain.notification.repository.NotificationRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DirectMessageNotificationIntegrationTest {

  @Autowired
  private ConversationService conversationService;

  @Autowired
  private ConversationRepository conversationRepository;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  @DisplayName("DM 전송 후 수신자 알림이 저장된다")
  void sendDirectMessageCreatesReceiverNotification() {
    User sender = saveUser("sender");
    User receiver = saveUser("receiver");
    Conversation conversation = conversationRepository.save(
        Conversation.between(sender.getId(), receiver.getId())
    );

    conversationService.sendDirectMessage(
        sender.getId(),
        conversation.getId(),
        new DirectMessageSendRequest("hello")
    );

    assertThat(notificationRepository.countByReceiverId(receiver.getId())).isEqualTo(1);
  }

  private User saveUser(String name) {
    String unique = UUID.randomUUID().toString().substring(0, 8);
    return userRepository.save(User.builder()
        .email(name + "-" + unique + "@ex.com")
        .passwordHash("password")
        .name(name)
        .build());
  }
}
