package com.eveassist.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    /** Reserved for the later REST API, which takes bearer tokens. A session cookie is never accepted here. */
    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain pageChain(HttpSecurity http, ClientRegistrationRepository registrations,
            KeycloakUserService users) throws Exception {
        var keycloakLogout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        keycloakLogout.setPostLogoutRedirectUri("{baseUrl}/");
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/", "/error", "/css/**", "/js/**").permitAll()
                        // a plan without an account: open to everyone, stores nothing, so it has no token to protect
                        .requestMatchers("/try", "/try/**").permitAll()
                        // the Compose health check and the alarm cannot sign in. Health shows only UP or DOWN, and exists
                        // only on the management port, which nothing publishes (the prod profile)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // metrics (heap, pool, queues) are for the operator, never an ordinary user
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/try/**"))
                .oauth2Login(login -> login
                        .loginPage("/oauth2/authorization/keycloak")
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(users)))
                .logout(logout -> logout.logoutSuccessHandler(keycloakLogout))
                .exceptionHandling(errors -> {
                    var expired = new ExpiredSessionHandler("/oauth2/authorization/keycloak");
                    errors.authenticationEntryPoint(expired);
                    errors.accessDeniedHandler(expired);
                })
                .build();
    }
}
