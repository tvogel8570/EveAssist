package com.eveassist.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KeycloakAdminTest {

    private HttpServer server;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<String> tokenForms = Collections.synchronizedList(new ArrayList<>());
    private final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
    private volatile int tokenStatus = 200;
    private volatile int deleteStatus = 204;

    @BeforeEach
    void startKeycloak() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            int status;
            byte[] body = new byte[0];
            if (exchange.getRequestURI().getPath().endsWith("/openid-connect/token")) {
                tokenForms.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                status = tokenStatus;
                body = "{\"access_token\":\"the-token\",\"expires_in\":300}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
            } else {
                authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
                status = deleteStatus;
            }
            exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            if (status != 204) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopKeycloak() {
        server.stop(0);
    }

    private KeycloakAdmin admin(String url, String secret) {
        return new KeycloakAdmin(RestClient.builder(), url, "EveAssist", "eveassist-admin", secret);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void deletesTheUserWithTheServiceAccountsToken() {
        var gone = admin(url(), "s3cret").deleteUser("user-sub-1");

        assertThat(gone).isTrue();
        assertThat(requests).containsExactly("POST /realms/EveAssist/protocol/openid-connect/token",
                "DELETE /admin/realms/EveAssist/users/user-sub-1");
        assertThat(tokenForms.getFirst()).contains("grant_type=client_credentials", "client_id=eveassist-admin",
                "client_secret=s3cret");
        assertThat(authorizations).containsExactly("Bearer the-token");
    }

    @Test
    void asksForTheTokenOnceAndKeepsItUntilItIsAboutToExpire() {
        var admin = admin(url(), "s3cret");

        admin.deleteUser("a");
        admin.deleteUser("b");

        assertThat(tokenForms).hasSize(1);
        assertThat(authorizations).hasSize(2);
    }

    @Test
    void aUserThatIsNotThereCountsAsGone() {
        deleteStatus = 404;

        assertThat(admin(url(), "s3cret").deleteUser("nobody")).isTrue();
    }

    @Test
    void anAnswerOtherThanDeletedOrNotFoundIsAFailure() {
        deleteStatus = 403;

        assertThat(admin(url(), "s3cret").deleteUser("user")).isFalse();
    }

    @Test
    void aRefusedTokenIsAFailureAndNothingIsDeleted() {
        tokenStatus = 401;

        assertThat(admin(url(), "wrong").deleteUser("user")).isFalse();
        assertThat(requests).containsExactly("POST /realms/EveAssist/protocol/openid-connect/token");
    }

    @Test
    void anUnreachableKeycloakIsAFailureNotAnException() {
        server.stop(0);

        assertThat(admin(url(), "s3cret").deleteUser("user")).isFalse();
    }

    @Test
    void withoutConfigurationItAsksNobodyAndSaysSo() {
        assertThat(admin("", "s3cret").deleteUser("user")).isFalse();
        assertThat(admin(url(), "").deleteUser("user")).isFalse();
        assertThat(requests).isEmpty();
    }
}
