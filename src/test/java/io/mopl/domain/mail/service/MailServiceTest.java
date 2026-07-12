package io.mopl.domain.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.mopl.domain.mail.exception.MailSendFailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

  @InjectMocks
  private MailService mailService;

  @Mock
  private JavaMailSender emailSender;

  @Test
  @DisplayName("임시 비밀번호 이메일 전송 성공")
  void sendTempPasswordEmail_Success() {
    String toEmail = "test@example.com";
    String tempPassword = "randomPassword123";

    mailService.sendTempPasswordEmail(toEmail, tempPassword);

    ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(emailSender).send(messageCaptor.capture());

    SimpleMailMessage sentMessage = messageCaptor.getValue();

    assertThat(sentMessage.getTo()).containsExactly(toEmail);
    assertThat(sentMessage.getSubject()).isEqualTo("[Mopl] 임시 비밀번호 발급 안내");
    assertThat(sentMessage.getText()).contains(tempPassword);
    assertThat(sentMessage.getText()).contains("3분 이내에 로그인하신 후, 반드시 비밀번호를 변경해 주세요.");
  }

  @Test
  @DisplayName("이메일 전송 실패 시 MailSendFailException 예외가 발생해야 한다")
  void sendTempPasswordEmail_Fail_ThrowsException() {
    String toEmail = "test@example.com";
    String tempPassword = "randomPassword123";

    doThrow(new MailSendException("SMTP 서버 에러"))
        .when(emailSender).send(any(SimpleMailMessage.class));

    assertThrows(MailSendFailException.class,
        () -> mailService.sendTempPasswordEmail(toEmail, tempPassword));
  }
}
