package io.mopl.global.security.oauth.service;

import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.SocialAccount;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.event.UserSyncedEvent;
import io.mopl.domain.user.repository.SocialAccountRepository;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.user.service.SocialAccountService;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.oauth.GoogleOAuth2UserInfo;
import io.mopl.global.security.oauth.KakaoOAuth2UserInfo;
import io.mopl.global.security.oauth.OAuth2UserInfo;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MoplOAuth2UserService extends DefaultOAuth2UserService {

  private final SocialAccountRepository socialAccountRepository;
  private final SocialAccountService socialAccountService;

  private final UserRepository userRepository;
  private final DomainEventPublisher eventPublisher;

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = loadOAuth2User(userRequest);
    String registrationId = userRequest.getClientRegistration().getRegistrationId();

    OAuth2UserInfo oAuth2UserInfo = createUserInfo(registrationId, oAuth2User.getAttributes());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth != null && auth.getPrincipal() instanceof MoplUserDetails userDetails) {
      try {
        socialAccountService.linkSocialAccount(
            userDetails.getUser().getId(),
            oAuth2UserInfo.getProvider(),
            oAuth2UserInfo.getProviderUserId(),
            oAuth2UserInfo.getEmail()
        );
        return userDetails;
      } catch (IllegalStateException e) {
        throw new OAuth2AuthenticationException(new OAuth2Error("oauth_failed"), "already_linked");
      }
    }

    SocialAccount socialAccount = socialAccountRepository
        .findByProviderAndProviderUserId(oAuth2UserInfo.getProvider(), oAuth2UserInfo.getProviderUserId())
        .orElseGet(() -> registerNewUser(oAuth2UserInfo));

    return new MoplUserDetails(socialAccount.getUser(), oAuth2User.getAttributes());
  }

  private SocialAccount registerNewUser(OAuth2UserInfo oAuth2UserInfo) {
    User user = userRepository.findByEmail(oAuth2UserInfo.getEmail())
        .orElseGet(() -> {
          String userName = oAuth2UserInfo.getName();
          if (userName == null || userName.isBlank()) {
            userName = "User_" + UUID.randomUUID().toString().substring(0, 6);
          }

          User newUser = User.builder()
              .email(oAuth2UserInfo.getEmail())
              .passwordHash("SOCIAL_LOGIN")
              .name(userName)
              .role(Role.USER)
              .build();

          User savedUser = userRepository.save(newUser);
          eventPublisher.publish(new UserSyncedEvent(
              savedUser.getId(),
              savedUser.getName(),
              savedUser.getEmail(),
              savedUser.getRole().name(),
              savedUser.isLocked(),
              savedUser.getCreatedAt()
          ));

          return savedUser;
        });

    SocialAccount newSocialAccount = SocialAccount.builder()
        .user(user)
        .provider(oAuth2UserInfo.getProvider())
        .providerUserId(oAuth2UserInfo.getProviderUserId())
        .providerEmail(oAuth2UserInfo.getEmail())
        .build();

    return socialAccountRepository.save(newSocialAccount);
  }

  private OAuth2UserInfo createUserInfo(String registrationId, Map<String, Object> attributes) {
    if ("google".equals(registrationId)) return new GoogleOAuth2UserInfo(attributes);
    if ("kakao".equals(registrationId)) return new KakaoOAuth2UserInfo(attributes);
    throw new OAuth2AuthenticationException(new OAuth2Error("oauth_failed"), "Unsupported provider");
  }

  public OAuth2User loadOAuth2User(OAuth2UserRequest userRequest) {
    return super.loadUser(userRequest);
  }
}
