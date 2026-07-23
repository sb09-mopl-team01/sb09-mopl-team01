package io.mopl.global.security.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import java.util.Base64;
import java.util.Optional;

@Component
public class RedisOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest>  {

  public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
  public static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
  private static final int COOKIE_EXPIRE_SECONDS = 180;
  private static final String REDIS_KEY_PREFIX = "oauth2_auth_request:";

  private final StringRedisTemplate redisTemplate;

  public RedisOAuth2AuthorizationRequestRepository(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
        .map(Cookie::getValue)
        .map(id -> redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + id))
        .map(serialized -> deserialize(serialized, OAuth2AuthorizationRequest.class))
        .orElse(null);
  }

  @Override
  public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request, HttpServletResponse response) {

    if (authorizationRequest == null) {
      removeAuthorizationRequestCookies(request, response);
      return;
    }

    String id = UUID.randomUUID().toString();

    String serializedRequest = serialize(authorizationRequest);
    redisTemplate.opsForValue().set(
        REDIS_KEY_PREFIX + id,
        serializedRequest,
        COOKIE_EXPIRE_SECONDS,
        TimeUnit.SECONDS
    );

    addCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, id, COOKIE_EXPIRE_SECONDS);

    String redirectUriAfterLogin = request.getParameter(REDIRECT_URI_PARAM_COOKIE_NAME);
    if (redirectUriAfterLogin != null && !redirectUriAfterLogin.isBlank()) {
      addCookie(response, REDIRECT_URI_PARAM_COOKIE_NAME, redirectUriAfterLogin, COOKIE_EXPIRE_SECONDS);
    }
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
      HttpServletResponse response) {
    try {
      return getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
          .map(Cookie::getValue)
          .map(id -> redisTemplate.opsForValue().getAndDelete(REDIS_KEY_PREFIX + id))
          .map(serialized -> deserialize(serialized, OAuth2AuthorizationRequest.class))
          .orElse(null);

    } finally {
      removeAuthorizationRequestCookies(request, response);
    }
  }

  public void removeAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
    deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
    deleteCookie(request, response, REDIRECT_URI_PARAM_COOKIE_NAME);
  }

  private Optional<Cookie> getCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null && cookies.length > 0) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(name)) return Optional.of(cookie);
      }
    }
    return Optional.empty();
  }

  private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
    Cookie cookie = new Cookie(name, value);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setMaxAge(maxAge);
    response.addCookie(cookie);
  }

  private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null && cookies.length > 0) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(name)) {
          cookie.setValue("");
          cookie.setPath("/");
          cookie.setMaxAge(0);
          cookie.setHttpOnly(true);
          cookie.setSecure(true);
          response.addCookie(cookie);
        }
      }
    }
  }

  private String serialize(Object object) {
    return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(object));
  }

  private <T> T deserialize(String base64, Class<T> cls) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(base64);
      return cls.cast(SerializationUtils.deserialize(decoded));
    } catch (Exception e) {
      return null;
    }
  }
}
