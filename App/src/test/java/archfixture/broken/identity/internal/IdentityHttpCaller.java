package archfixture.broken.identity.internal;

/** Another class of identity that starts an HTTP call: the exemption is KeycloakAdmin only, so this is still flagged. */
@org.springframework.stereotype.Component
public class IdentityHttpCaller {
    org.springframework.web.client.RestClient client;
}
