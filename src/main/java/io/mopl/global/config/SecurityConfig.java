package io.mopl.global.config;

import io.mopl.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(this::configureCsrf)

        .formLogin(this::configureFormLogin)

        .httpBasic(this::configureHttpBasic)

        .sessionManagement(this::configureSessionManagement)

        .authorizeHttpRequests(this::configureAuthorizeRequests)

        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

    ;

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private void configureCsrf(CsrfConfigurer<HttpSecurity> csrf) {
    csrf.disable();
  }

  private void configureFormLogin(FormLoginConfigurer<HttpSecurity> login) {
    login.disable();
  }

  private void configureHttpBasic(HttpBasicConfigurer<HttpSecurity> basic) {
    basic.disable();
  }

  private void configureSessionManagement(SessionManagementConfigurer<HttpSecurity> session) {
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
  }

  private void configureAuthorizeRequests(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
    auth
        .requestMatchers("/index.html", "/*.ico", "/assets/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/users").permitAll() // 회원가입
        .requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll() // 로그인
        .requestMatchers("/h2-console/**").permitAll()
        .anyRequest().authenticated();
        // 테스트 시 보안 해제 하고싶으면 사용
        //.anyRequest().permitAll();
  }
}