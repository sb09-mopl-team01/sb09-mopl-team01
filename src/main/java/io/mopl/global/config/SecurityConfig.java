package io.mopl.global.config;

import io.mopl.global.security.csrf.CsrfCookieFilter;
import io.mopl.global.security.csrf.StatelessCsrfTokenRepository;
import io.mopl.global.security.filter.MoplLoginFilter;
import io.mopl.global.security.handler.LoginFailureHandler;
import io.mopl.global.security.handler.LoginSuccessHandler;
import io.mopl.global.security.handler.MoplLogoutHandler;
import io.mopl.global.security.handler.MoplLogoutSuccessHandler;
import io.mopl.global.security.handler.SpaCsrfTokenRequestHandler;
import io.mopl.global.security.jwt.JwtAuthenticationFilter;
import io.mopl.global.security.oauth.RedisOAuth2AuthorizationRequestRepository;
import io.mopl.global.security.oauth.handler.OAuth2LoginFailureHandler;
import io.mopl.global.security.oauth.handler.OAuth2LoginSuccessHandler;
import io.mopl.global.security.oauth.service.MoplOAuth2UserService;
import jakarta.servlet.DispatcherType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.*;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final LoginSuccessHandler loginSuccessHandler;
  private final LoginFailureHandler loginFailureHandler;
  private final MoplLogoutHandler logoutHandler;
  private final MoplLogoutSuccessHandler logoutSuccessHandler;
  private final StatelessCsrfTokenRepository csrfTokenRepository;
  private final MoplOAuth2UserService moplOAuth2UserService;
  private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
  private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
  private final RedisOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

  @Value("${mopl.cors.allowed-origins}")
  private List<String> allowedOrigins;
  @Value("${mopl.cors.allowed-methods}")
  private List<String> allowedMethods;
  @Value("${mopl.cors.allowed-headers}")
  private List<String> allowedHeaders;

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      AuthenticationManager authenticationManager,
      CsrfCookieFilter csrfCookieFilter
  ) throws Exception {
    http
        .cors(this::configureCors)
        .csrf(this::configureCsrf)
        .oauth2Login(this::configureOAuth2Login)
        .httpBasic(this::configureHttpBasic)
        .formLogin(this::configureFormLogin)
        .logout(this::configureLogout)
        .sessionManagement(this::configureSessionManagement)
        .authorizeHttpRequests(this::configureAuthorizeRequests)
        .headers(this::configureHeaders)
        .addFilterAfter(csrfCookieFilter, CsrfFilter.class);

    this.configureCustomFilters(http, authenticationManager);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(allowedMethods);
    configuration.setAllowedHeaders(allowedHeaders);
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private void configureCors(CorsConfigurer<HttpSecurity> cors) {
    cors.configurationSource(corsConfigurationSource());
  }

  private void configureCsrf(CsrfConfigurer<HttpSecurity> csrf) {
    csrf.csrfTokenRepository(csrfTokenRepository)
        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
        .ignoringRequestMatchers("/h2-console/**", "/api/auth/refresh", "/ws/**", "/api/auth/sign-out");
  }

  private void configureOAuth2Login(OAuth2LoginConfigurer<HttpSecurity> oauth2) {
    oauth2
        .authorizationEndpoint(endpoint -> endpoint
            .authorizationRequestRepository(cookieAuthorizationRequestRepository)
        )
        .userInfoEndpoint(userInfo -> userInfo.userService(moplOAuth2UserService))
        .successHandler(oAuth2LoginSuccessHandler)
        .failureHandler(oAuth2LoginFailureHandler);
  }

  private void configureHttpBasic(HttpBasicConfigurer<HttpSecurity> basic) {
    basic.disable();
  }

  private void configureFormLogin(FormLoginConfigurer<HttpSecurity> login) {
    login.disable();
  }

  private void configureLogout(LogoutConfigurer<HttpSecurity> logout) {
    logout.logoutUrl("/api/auth/sign-out")
        .addLogoutHandler(logoutHandler)
        .logoutSuccessHandler(logoutSuccessHandler)
        .invalidateHttpSession(false);
  }

  private void configureSessionManagement(SessionManagementConfigurer<HttpSecurity> session) {
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy());
  }

  private void configureHeaders(HeadersConfigurer<HttpSecurity> headers) {
    headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin);
  }

  private void configureAuthorizeRequests(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
    auth
        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/auth/csrf-token").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
        .requestMatchers("/", "/error").permitAll()
        .requestMatchers("/index.html", "/*.ico", "/assets/**", "/*.svg").permitAll()
        .requestMatchers("/h2-console/**").permitAll()
        .requestMatchers("/ws/**").permitAll()
        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
        .requestMatchers("/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus").hasRole("ADMIN")
        .anyRequest().authenticated();
  }

  private void configureCustomFilters(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    MoplLoginFilter moplLoginFilter = new MoplLoginFilter(authenticationManager);
    moplLoginFilter.setAuthenticationSuccessHandler(loginSuccessHandler);
    moplLoginFilter.setAuthenticationFailureHandler(loginFailureHandler);

    http
        .addFilterAt(moplLoginFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
  }
}
