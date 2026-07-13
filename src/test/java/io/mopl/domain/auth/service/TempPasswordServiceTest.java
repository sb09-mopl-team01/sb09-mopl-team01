package io.mopl.domain.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TempPasswordServiceTest {

  @InjectMocks
  private TempPasswordService tempPasswordService;

  @Mock
  private RedisTemplate<String, Object> redisTemplate;

  @Mock
  private ValueOperations<String, Object> valueOperations;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("generateRandomPassword - 10자리 무작위 문자열 생성")
  void generateRandomPassword_Success() {
    String password = tempPasswordService.generateRandomPassword();
    assertNotNull(password);
    assertEquals(10, password.length());
  }

  @Test
  @DisplayName("saveTempPassword - Redis에 3분 만료로 정상 저장")
  void saveTempPassword_Success() {
    UUID userId = UUID.randomUUID();
    String tempPw = "encodedPw123";

    tempPasswordService.saveTempPassword(userId, tempPw);

    verify(valueOperations).set("TEMP_PW:" + userId, tempPw, 3, TimeUnit.MINUTES);
  }

  @Test
  @DisplayName("getTempPassword - 존재하는 임시 비밀번호 조회")
  void getTempPassword_Exists_ReturnsPassword() {
    UUID userId = UUID.randomUUID();
    when(valueOperations.get("TEMP_PW:" + userId)).thenReturn("encodedPw123");

    String result = tempPasswordService.getTempPassword(userId);

    assertEquals("encodedPw123", result);
  }

  @Test
  @DisplayName("getTempPassword - 존재하지 않으면 null 반환")
  void getTempPassword_NotExists_ReturnsNull() {
    UUID userId = UUID.randomUUID();
    when(valueOperations.get("TEMP_PW:" + userId)).thenReturn(null);

    String result = tempPasswordService.getTempPassword(userId);

    assertNull(result);
  }

  @Test
  @DisplayName("deleteTempPassword - Redis 키 삭제 요청")
  void deleteTempPassword_Success() {
    UUID userId = UUID.randomUUID();

    tempPasswordService.deleteTempPassword(userId);

    verify(redisTemplate).delete("TEMP_PW:" + userId);
  }
}