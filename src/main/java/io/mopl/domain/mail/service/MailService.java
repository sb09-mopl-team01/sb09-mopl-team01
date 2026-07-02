package io.mopl.domain.mail.service;

import io.mopl.domain.mail.exception.MailSendFailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

  private final JavaMailSender emailSender;

  public void sendTempPasswordEmail(String toEmail, String tempPassword) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(toEmail);
    message.setSubject("[Mopl] 임시 비밀번호 발급 안내");
    message.setText("안녕하세요.\n\n" +
        "요청하신 임시 비밀번호가 발급되었습니다.\n" +
        "임시 비밀번호: " + tempPassword + "\n\n" +
        "보안을 위해 3분 이내에 로그인하신 후, 반드시 비밀번호를 변경해 주세요.");

    try {
      emailSender.send(message);
      log.debug("MailService Send Temp Password Success: {}}", toEmail);
    } catch (Exception e) {
      log.error("MailService Send Temp Password Fail: {}}", toEmail);
      throw new MailSendFailException();
    }
  }
}
