package io.mopl.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.global.security.oauth.RedisOAuth2AuthorizationRequestRepository;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;

@ExtendWith(MockitoExtension.class)
class RedisOAuth2AuthorizationRequestRepositoryTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @InjectMocks
  private RedisOAuth2AuthorizationRequestRepository repository;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("저장 시 브라우저 쿠키에는 난수 ID만 담기고, 실제 데이터는 Redis에 저장된다")
  void saveAuthorizationRequest() {
    OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
        .clientId("test-client")
        .state("test-state")
        .authorizationUri("https://test.com/auth")
        .build();

    repository.saveAuthorizationRequest(authRequest, request, response);

    Cookie savedCookie = response.getCookie(RedisOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
    assertThat(savedCookie).isNotNull();
    String opaqueId = savedCookie.getValue();
    assertThat(opaqueId).isNotBlank();

    String expectedRedisKey = "oauth2_auth_request:" + opaqueId;
    verify(valueOperations).set(
        eq(expectedRedisKey),
        anyString(),
        eq(180L),
        eq(TimeUnit.SECONDS)
    );
  }

  @Test
  @DisplayName("쿠키의 ID를 통해 Redis에서 인증 요청 데이터를 정상적으로 복원한다")
  void loadAuthorizationRequest() {
    String testId = "test-uuid-1234";
    request.setCookies(new Cookie(RedisOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, testId));

    OAuth2AuthorizationRequest mockAuthRequest = OAuth2AuthorizationRequest.authorizationCode()
        .clientId("test-client")
        .state("test-state")
        .authorizationUri("https://test.com/auth")
        .build();

    String serializedData = Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(mockAuthRequest));
    when(valueOperations.get("oauth2_auth_request:" + testId)).thenReturn(serializedData);

    OAuth2AuthorizationRequest loadedRequest = repository.loadAuthorizationRequest(request);

    assertThat(loadedRequest).isNotNull();
    assertThat(loadedRequest.getState()).isEqualTo("test-state");
    assertThat(loadedRequest.getClientId()).isEqualTo("test-client");
  }

  @Test
  @DisplayName("콜백 처리 시(remove) 값을 반환하며 Redis 내부 데이터와 브라우저 쿠키를 모두 즉시 파기한다")
  void removeAuthorizationRequest() {
    String testId = "test-uuid-1234";
    request.setCookies(new Cookie(RedisOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, testId));

    OAuth2AuthorizationRequest mockAuthRequest = OAuth2AuthorizationRequest.authorizationCode()
        .clientId("test-client")
        .state("test-state")
        .authorizationUri("https://test.com/auth")
        .build();
    String serializedData = Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(mockAuthRequest));

    when(valueOperations.getAndDelete("oauth2_auth_request:" + testId)).thenReturn(serializedData);

    OAuth2AuthorizationRequest removedRequest = repository.removeAuthorizationRequest(request, response);

    assertThat(removedRequest).isNotNull();
    assertThat(removedRequest.getState()).isEqualTo("test-state");

    verify(valueOperations).getAndDelete("oauth2_auth_request:" + testId);

    Cookie deletedCookie = response.getCookie(RedisOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
    assertThat(deletedCookie).isNotNull();
    assertThat(deletedCookie.getMaxAge()).isZero();
  }

  @Test
  @DisplayName("Redis에 변조되거나 잘못된 데이터가 있어도 500 에러로 전파되지 않고 조용히 실패(null)한다")
  void invalidDataDoesNotThrow500() {
    String testId = "test-uuid-1234";
    request.setCookies(new Cookie(RedisOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, testId));

    when(valueOperations.get("oauth2_auth_request:" + testId)).thenReturn("invalid-base64-string-hacked-!@#");

    OAuth2AuthorizationRequest result = repository.loadAuthorizationRequest(request);
    assertThat(result).isNull();
  }
}