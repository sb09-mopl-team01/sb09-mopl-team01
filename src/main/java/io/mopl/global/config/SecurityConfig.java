package io.mopl.global.config;

import io.mopl.global.security.filter.MoplLoginFilter;
import io.mopl.global.security.handler.LoginFailureHandler;
import io.mopl.global.security.handler.LoginSuccessHandler;
import io.mopl.global.security.handler.MoplLogoutHandler;
import io.mopl.global.security.handler.MoplLogoutSuccessHandler;
import io.mopl.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {

    http
        .csrf(this::configureCsrf)
        .formLogin(this::configureFormLogin)
        .logout(this::configureLogout)
        .sessionManagement(this::configureSessionManagement)
        .authorizeHttpRequests(this::configureAuthorizeRequests);

    this.configureCustomFilters(http, authenticationManager);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }


  private void configureCustomFilters(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    MoplLoginFilter moplLoginFilter = new MoplLoginFilter(authenticationManager);
    moplLoginFilter.setAuthenticationSuccessHandler(loginSuccessHandler);
    moplLoginFilter.setAuthenticationFailureHandler(loginFailureHandler);

    http
        .addFilterAt(moplLoginFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
  }

  private void configureCsrf(CsrfConfigurer<HttpSecurity> csrf) {
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.csrfTokenRepository(csrfTokenRepository)
        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
        .ignoringRequestMatchers("/h2-console/**", "/api/auth/refresh");
  }

  private void configureFormLogin(FormLoginConfigurer<HttpSecurity> login) {
    login.disable();
  }

  private void configureLogout(LogoutConfigurer<HttpSecurity> logout) {
    logout.logoutUrl("/api/auth/sign-out")
        .addLogoutHandler(logoutHandler)
        .logoutSuccessHandler(logoutSuccessHandler)
        .invalidateHttpSession(false)
        .deleteCookies("REFRESH_TOKEN");
  }

  private void configureHttpBasic(HttpBasicConfigurer<HttpSecurity> basic) {
    basic.disable();
  }

  private void configureSessionManagement(SessionManagementConfigurer<HttpSecurity> session) {
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
  }

  private void configureAuthorizeRequests(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
    auth
        .requestMatchers(HttpMethod.POST, "/api/users").permitAll() // 회원가입
        .requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll() // 로그인
        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll() // 새로고침
        .requestMatchers(HttpMethod.GET, "/api/auth/csrf-token").permitAll()

        .requestMatchers("/index.html", "/*.ico", "/assets/**").permitAll()
        .requestMatchers("/h2-console/**").permitAll()

        .anyRequest().authenticated();

        // 테스트 시 보안 해제 하고싶으면 사용
        //.anyRequest().permitAll();
  }
}