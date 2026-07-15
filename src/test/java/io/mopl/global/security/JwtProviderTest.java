package io.mopl.global.security;

import com.nimbusds.jose.JOSEException;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.jwt.JwtProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Base64;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class JwtProviderTest {

  private JwtProvider jwtProvider;
  private final String SECRET_KEY = Base64.getEncoder().encodeToString("my-very-secret-key-that-is-at-least-32-bytes-long!".getBytes());
  private final long ACCESS_TOKEN_VALIDITY = 3600L;
  private final long REFRESH_TOKEN_VALIDITY = 86400L;

  @BeforeEach
  void setUp() throws JOSEException {
    jwtProvider = new JwtProvider(SECRET_KEY, ACCESS_TOKEN_VALIDITY, REFRESH_TOKEN_VALIDITY);
  }

  @Test
  @DisplayName("액세스 토큰 생성 및 검증 성공")
  void generateAndValidateAccessToken_Success() {
    MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);

    io.mopl.domain.user.entity.User mockEntityUser = mock(io.mopl.domain.user.entity.User.class);

    given(mockUserDetails.getUsername()).willReturn("test@example.com");

    doReturn(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
        .when(mockUserDetails).getAuthorities();

    given(mockUserDetails.getUser()).willReturn(mockEntityUser);
    given(mockEntityUser.getId()).willReturn(UUID.randomUUID());

    String token = jwtProvider.generateAccessToken(mockUserDetails);

    assertNotNull(token);
    assertTrue(jwtProvider.validateToken(token));
    assertEquals("test@example.com", jwtProvider.getUsername(token));
  }

  @Test
  @DisplayName("리프레시 토큰 생성 및 userId 추출 성공")
  void generateRefreshToken_Success() {
    String email = "test@example.com";
    String userIdStr = UUID.randomUUID().toString();

    String token = jwtProvider.generateRefreshToken(email, userIdStr);

    assertNotNull(token);
    assertTrue(jwtProvider.validateToken(token));
    assertEquals(email, jwtProvider.getUsername(token));
    assertEquals(UUID.fromString(userIdStr), jwtProvider.getUserId(token));
  }

  @Test
  @DisplayName("잘못된 형식의 토큰 검증 시 false 반환")
  void validateToken_InvalidFormat_ReturnsFalse() {
    String invalidToken = "invalid.token.string";

    assertFalse(jwtProvider.validateToken(invalidToken));
  }

  @Test
  @DisplayName("validateToken - 만료된 토큰인 경우 false 반환")
  void validateToken_ExpiredToken_ReturnsFalse() throws Exception {
    JwtProvider expiredJwtProvider = new JwtProvider(SECRET_KEY, 0L, 0L);

    MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);
    io.mopl.domain.user.entity.User mockEntityUser = mock(io.mopl.domain.user.entity.User.class);

    given(mockUserDetails.getUsername()).willReturn("test@example.com");
    doReturn(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
        .when(mockUserDetails).getAuthorities();
    given(mockUserDetails.getUser()).willReturn(mockEntityUser);
    given(mockEntityUser.getId()).willReturn(UUID.randomUUID());

    String expiredToken = expiredJwtProvider.generateAccessToken(mockUserDetails);

    Thread.sleep(10);

    boolean isValid = jwtProvider.validateToken(expiredToken);
    assertFalse(isValid);
  }

  @Test
  @DisplayName("getUsername - 형식이 잘못된 토큰 파싱 시 RuntimeException 발생")
  void getUsername_ParseException_ThrowsBaseException() {
    String invalidToken = "invalid.token.string";
    BaseException exception = assertThrows(BaseException.class,
        () -> jwtProvider.getUsername(invalidToken));

    assertEquals(ErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
  }

  @Test
  @DisplayName("generateAccessToken - 서명 과정에서 오류 발생 시 RuntimeException 발생")
  void generateAccessToken_SignatureException_ThrowsBaseException() throws Exception {
    JwtProvider faultProvider = new JwtProvider(SECRET_KEY, 3600L, 86400L);

    BaseException exception = assertThrows(BaseException.class,
        () -> faultProvider.generateAccessToken(null));

    assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
  }
}
