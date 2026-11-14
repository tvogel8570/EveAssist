package com.eveassist.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    /**
     * The management port (the prod profile: 8081, which nothing publishes and Caddy does not proxy) serves health and metrics without a
     * sign-in, as the runbooks and the Compose health check rely on. The page chain would otherwise apply there too and send every
     * metrics request to the Keycloak login. Only when the management port is a port of its own, and only those two endpoints on it: the same paths on the app port stay with the
     * page chain (metrics for an admin), and anything else on the management port is refused. Without a separate management port (dev)
     * nothing matches here.
     */
    @Bean
    @Order(0)
    SecurityFilterChain managementChain(HttpSecurity http, Environment environment) throws Exception {
        RequestMatcher onTheManagementPort = (HttpServletRequest request) -> {
            var management = environment.getProperty("local.management.port", Integer.class);
            var server = environment.getProperty("local.server.port", Integer.class);
            // without a port of its own, the management port is reported as the server's: then this chain must not take the app's requests
            return management != null && !management.equals(server) && request.getLocalPort() == management;
        };
        return http.securityMatcher(onTheManagementPort)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/metrics",
                                "/actuator/metrics/**").permitAll()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

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
                        // the policy and the way to report a vulnerability are for anyone to read
                        .requestMatchers("/privacy", "/.well-known/security.txt").permitAll()
                        // a plan without an account: open to everyone, stores nothing, so it has no token to protect
                        .requestMatchers("/try", "/try/**").permitAll()
                        // the Compose health check and the alarm cannot sign in. Health shows only UP or DOWN, and exists
                        // only on the management port, which nothing publishes (the prod profile); managementChain serves it
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
