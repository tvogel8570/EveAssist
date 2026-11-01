package com.eveassist.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.PostgresTestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignInIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    @LocalServerPort
    int port;

    @Test
    void homePageIsPublicAndOffersSignIn() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"sign-in\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("??"))));
    }

    @Test
    void otherPagesSendAVisitorToSignIn() throws Exception {
        mvc.perform(get("/account"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
    }

    @Test
    void accountPageShowsTheAccount() throws Exception {
        var sub = UUID.randomUUID().toString();
        var account = accounts.signedIn(sub, "Where Am I");

        mvc.perform(get("/account").with(oidcLogin()
                        .idToken(token -> token.subject(sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Where Am I")))
                .andExpect(content().string(Matchers.containsString(account.id().value().toString())))
                .andExpect(content().string(Matchers.containsString("ROLE_USER")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("??"))));
    }

    @Test
    void repeatedSignInKeepsTheAccountAndItsName() {
        var sub = UUID.randomUUID().toString();
        var first = accounts.signedIn(sub, "First Name");
        var second = accounts.signedIn(sub, "Changed At Provider");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void differentKeycloakUsersWithTheSameNameGetDifferentAccounts() {
        var one = accounts.signedIn(UUID.randomUUID().toString(), "Same Name");
        var other = accounts.signedIn(UUID.randomUUID().toString(), "Same Name");

        assertThat(one.id()).isNotEqualTo(other.id());
    }

    @Test
    void apiIsClosedUntilItIsBuilt() throws Exception {
        mvc.perform(get("/api/v1/plans").with(oidcLogin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/plans")).andExpect(status().isUnauthorized());
    }

    @Test
    void signOutNeedsCsrfTokenAndEndsAtKeycloakOrHome() throws Exception {
        mvc.perform(post("/logout").with(oidcLogin())).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(oidcLogin()).with(csrf())).andExpect(status().isFound());
    }

    @Test
    void sessionCookieIsHttpOnlySecureAndLax() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/authorization/keycloak"))
                .GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

        var cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(cookie).startsWith("SESSION=").contains("HttpOnly").contains("Secure").contains("SameSite=Lax");
    }
}
