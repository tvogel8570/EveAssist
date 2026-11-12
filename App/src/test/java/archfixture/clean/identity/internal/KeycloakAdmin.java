package archfixture.clean.identity.internal;

/** The one class of identity that may call Keycloak's admin API. */
@org.springframework.stereotype.Component
public class KeycloakAdmin {
    org.springframework.web.client.RestClient client;
}
