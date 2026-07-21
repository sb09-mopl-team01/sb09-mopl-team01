package io.mopl.global.security.oauth;

public interface OAuth2UserInfo {
  String getProvider();
  String getProviderUserId();
  String getEmail();
  String getName();
}
