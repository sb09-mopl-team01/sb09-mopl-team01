package io.mopl.domain.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController
public class HealthCheckController {
  // 분산 환경 테스트 용
  @GetMapping("/api/server-check")
  public String checkServer() throws UnknownHostException {
    String ip = InetAddress.getLocalHost().getHostAddress();
    return "현재 응답하고 있는 서버의 프라이빗 IP: " + ip;
  }
}
