package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpEveSsoTest {

    private static final EveSsoProperties PROPERTIES = new EveSsoProperties("client-id", "client-secret",
            "https://app.test/characters/link/callback", "https://sso.test/authorize", "https://sso.test/token",
            "https://sso.test/meta", "https://sso.test/revoke");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpEveSso sso = new HttpEveSso(builder, PROPERTIES, Clock.systemUTC());

    @Test
    void revokesARefreshTokenWithTheApplicationsCredentials() {
        server.expect(requestTo("https://sso.test/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString("client-id:client-secret".getBytes())))
                .andExpect(content().string(containsString("token=refresh-abc")))
                .andExpect(content().string(containsString("token_type_hint=refresh_token")))
                .andRespond(withSuccess());

        assertThat(sso.revoke("refresh-abc")).isTrue();
        server.verify();
    }

    @Test
    void reportsAFailedRevocationWithoutThrowing() {
        server.expect(requestTo("https://sso.test/revoke")).andRespond(withServerError());

        assertThat(sso.revoke("refresh-abc")).isFalse();
    }

    @Test
    void asksForExactlyTheGivenScopesAndStateWhenSendingTheBrowserToSso() {
        var url = sso.authorizeUrl("a-state", java.util.Set.of("scope.b", "scope.a"));

        assertThat(url).startsWith("https://sso.test/authorize?").contains("response_type=code")
                .contains("client_id=client-id").contains("state=a-state")
                .contains("scope=scope.a%20scope.b")
                .contains("redirect_uri=https://app.test/characters/link/callback")
                .doesNotContain("client-secret");
    }
}
