package com.eveassist.identity.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Signs a browser test in with a real session, because tests have no Keycloak. Exists only in test sources. */
@TestConfiguration
public class TestSignIn {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> testSignInFilter(AccountService accounts) {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws IOException, ServletException {
                if (!request.getRequestURI().equals("/test-sign-in")) {
                    chain.doFilter(request, response);
                    return;
                }
                var sub = request.getParameter("sub");
                accounts.signedIn(sub, "Test Pilot");
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                var token = new OidcIdToken("test", Instant.now(), Instant.now().plusSeconds(3600), Map.of("sub", sub));
                var user = new DefaultOidcUser(authorities, token, "sub");
                var context = new SecurityContextImpl(new OAuth2AuthenticationToken(user, authorities, "keycloak"));
                request.getSession().setAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
                response.sendRedirect("/plans");
            }
        });
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER + 1);
        return registration;
    }
}
