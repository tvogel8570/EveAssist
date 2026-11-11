package com.eveassist;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The prod profile's promises, with the application started as it is behind Caddy: the visitor's address comes from
 * X-Forwarded-For (without it every visitor would count as Caddy and share one fetch allowance), and health is served on
 * a management port of its own, without a sign-in. The token data key is unwrapped by KMS, as in prod, through the real AWS
 * client pointed at a fake KMS, because the prod profile refuses a plain key from the environment.
 */
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "management.server.port=0",
        // the test profile's plain key is replaced by a wrapped one
        "eveassist.characters.token-data-key=",
        "eveassist.characters.token-data-key-wrapped=AAECAwQFBgcICQ==",
        "eveassist.characters.kms-key-id=alias/eveassist-token-key"})
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

    private static HttpServer fakeKms;
    private static volatile String kmsRequest = "";

    /** Answers every KMS call as Decrypt, with a 32-byte key, and remembers what it was asked. */
    @DynamicPropertySource
    static void kms(DynamicPropertyRegistry registry) throws IOException {
        fakeKms = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeKms.createContext("/", exchange -> {
            kmsRequest = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var body = ("{\"KeyId\":\"test\",\"EncryptionAlgorithm\":\"SYMMETRIC_DEFAULT\",\"Plaintext\":\""
                    + Base64.getEncoder().encodeToString(new byte[32]) + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/x-amz-json-1.1");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeKms.start();
        System.setProperty("aws.endpointUrl", "http://127.0.0.1:" + fakeKms.getAddress().getPort());
        System.setProperty("aws.region", "us-west-2");
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @AfterAll
    static void stopKms() {
        fakeKms.stop(0);
        for (var name : new String[] {"aws.endpointUrl", "aws.region", "aws.accessKeyId", "aws.secretAccessKey"}) {
            System.clearProperty(name);
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
    void theTokenDataKeyWasUnwrappedByKmsForItsPurpose() {
        assertThat(kmsRequest).contains("alias/eveassist-token-key").contains("eveassist-refresh-tokens");
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
