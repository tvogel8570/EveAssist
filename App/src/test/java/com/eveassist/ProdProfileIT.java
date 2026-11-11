package com.eveassist;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The prod profile's promises, with the application started as it is behind Caddy: the visitor's address comes from
 * X-Forwarded-For (without it every visitor would count as Caddy and share one fetch allowance), and health is served on
 * a management port of its own, without a sign-in.
 */
@ActiveProfiles("prod")
@TestPropertySource(properties = "management.server.port=0")
@Import(ProdProfileIT.RemoteAddresses.class)
class ProdProfileIT extends IntegrationTest {

    /** Records the address the application sees for each request, after the proxy headers were applied. */
    static class RemoteAddresses extends OncePerRequestFilter {
        final CopyOnWriteArrayList<String> seen = new CopyOnWriteArrayList<>();

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            seen.add(request.getRemoteAddr());
            chain.doFilter(request, response);
        }
    }

    @Value("${local.server.port}")
    int appPort;

    @Value("${local.management.port}")
    int managementPort;

    @Autowired
    RemoteAddresses remoteAddresses;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(int port, String path, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theVisitorsAddressIsTheOneCaddyForwarded() throws Exception {
        remoteAddresses.seen.clear();

        get(appPort, "/", "X-Forwarded-For", "203.0.113.7");

        assertThat(remoteAddresses.seen).containsExactly("203.0.113.7");
    }

    @Test
    void healthIsOnTheManagementPortWithoutSignIn() throws Exception {
        var response = get(managementPort, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void healthIsNotOnTheAppPort() throws Exception {
        var response = get(appPort, "/actuator/health");

        assertThat(response.statusCode()).isNotEqualTo(200);
    }
}
