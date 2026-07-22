package io.mopl.global.security.oauth2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.SocialAccount;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.SocialAccountRepository;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.user.service.SocialAccountService;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.oauth.service.MoplOAuth2UserService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MoplOAuth2UserServiceTest {

  @Mock private SocialAccountRepository socialAccountRepository;
  @Mock private SocialAccountService socialAccountService;
  @Mock private UserRepository userRepository;

  @Mock private DomainEventPublisher eventPublisher;

  @Spy
  @InjectMocks
  private MoplOAuth2UserService moplOAuth2UserService;

  @Mock private OAuth2UserRequest userRequest;
  @Mock private ClientRegistration clientRegistration;
  @Mock private OAuth2User oAuth2User;

  private UUID userId;
  private User mockUser;
  private Map<String, Object> attributes;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    mockUser = User.builder().email("test@gmail.com").name("Test").role(Role.USER).build();
    ReflectionTestUtils.setField(mockUser, "id", userId);

    attributes = Map.of("sub", "12345", "email", "test@gmail.com", "name", "Test");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void setAuthentication(Authentication auth) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
  }

  private void mockOAuth2UserCall(String provider) {
    when(userRequest.getClientRegistration()).thenReturn(clientRegistration);
    when(clientRegistration.getRegistrationId()).thenReturn(provider);

    doReturn(oAuth2User).when(moplOAuth2UserService).loadOAuth2User(userRequest);
    when(oAuth2User.getAttributes()).thenReturn(attributes);
  }

  @Test
  @DisplayName("기존에 가입된 소셜 계정이 있으면 해당 유저 정보를 반환한다")
  void loadUser_LoginExistingSocialAccount() {
    setAuthentication(null);
    mockOAuth2UserCall("google");

    SocialAccount existingAccount = SocialAccount.builder().user(mockUser).build();
    when(socialAccountRepository.findByProviderAndProviderUserId("google", "12345"))
        .thenReturn(Optional.of(existingAccount));

    OAuth2User result = moplOAuth2UserService.loadUser(userRequest);

    assertThat(result).isInstanceOf(MoplUserDetails.class);
    assertThat(((MoplUserDetails) result).getUser()).isEqualTo(mockUser);
    verify(userRepository, never()).save(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("소셜 계정도 없고 유저도 없으면 둘 다 신규 생성(Register)한다")
  void loadUser_RegisterNewUserAndAccount() {
    setAuthentication(null);
    mockOAuth2UserCall("google");

    when(socialAccountRepository.findByProviderAndProviderUserId("google", "12345"))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenReturn(mockUser);

    SocialAccount newSocialAccount = SocialAccount.builder().user(mockUser).build();
    when(socialAccountRepository.save(any(SocialAccount.class))).thenReturn(newSocialAccount);

    moplOAuth2UserService.loadUser(userRequest);

    verify(userRepository).save(any(User.class));
    verify(socialAccountRepository).save(any(SocialAccount.class));
    verify(eventPublisher).publish(any());
  }

  @Test
  @DisplayName("지원하지 않는 Provider가 들어오면 예외가 발생한다")
  void loadUser_UnsupportedProvider() {
    mockOAuth2UserCall("github");

    assertThrows(OAuth2AuthenticationException.class,
        () -> moplOAuth2UserService.loadUser(userRequest));
  }
}