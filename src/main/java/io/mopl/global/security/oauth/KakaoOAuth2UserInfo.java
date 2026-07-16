package io.mopl.global.security.oauth;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

  private final Map<String, Object> attributes;

  public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
    this.attributes = attributes;
  }

  @Override
  public String getProvider() {
    return "kakao";
  }

  @Override
  public String getProviderUserId() {
    return String.valueOf(attributes.get("id"));
  }

  @Override
  public String getEmail() {
    Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
    String nickname = properties != null ? (String) properties.get("nickname") : "unknown";
    String id = getProviderUserId();
    return nickname + "_" + id + "@kakao.com";
  }

  @Override
  public String getName() {
    Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
    return properties != null ? (String) properties.get("nickname") : "unknown";
  }
}
