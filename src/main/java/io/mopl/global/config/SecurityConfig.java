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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
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
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> configureCsrf(csrf, csrfTokenRepository))
        .httpBasic(this::configureHttpBasic)
        .addFilterAfter(csrfCookieFilter, CsrfFilter.class)
        .formLogin(this::configureFormLogin)
        .logout(this::configureLogout)
        .sessionManagement(this::configureSessionManagement)
        .authorizeHttpRequests(this::configureAuthorizeRequests);

    this.configureCustomFilters(http, authenticationManager);

    return http.build();
  }

  private void configureCsrf(CsrfConfigurer<HttpSecurity> csrf, CsrfTokenRepository repository) {
    csrf.csrfTokenRepository(repository)
        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
        .ignoringRequestMatchers("/h2-console/**", "/api/auth/refresh", "/ws/**", "/api/auth/sign-out");
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

  private void configureCustomFilters(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    MoplLoginFilter moplLoginFilter = new MoplLoginFilter(authenticationManager);
    moplLoginFilter.setAuthenticationSuccessHandler(loginSuccessHandler);
    moplLoginFilter.setAuthenticationFailureHandler(loginFailureHandler);

    http
        .addFilterAt(moplLoginFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy());
  }

  private void configureAuthorizeRequests(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
    auth
        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/auth/csrf-token").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
        .requestMatchers("/", "/error").permitAll()
        .requestMatchers("/index.html", "/*.ico", "/assets/**", "/*.svg").permitAll()
        .requestMatchers("/h2-console/**").permitAll()
        .requestMatchers("/ws/**").permitAll()
        .requestMatchers("/actuator/health").permitAll()
        .anyRequest().authenticated();
  }
}
