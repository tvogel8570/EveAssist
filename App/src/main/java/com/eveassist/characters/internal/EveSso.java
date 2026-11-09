package com.eveassist.characters.internal;

import java.util.Set;

/** EVE SSO, as far as linking a character needs it. The one implementation talks HTTP, tests use a fake. */
interface EveSso {

    /** The address the browser is sent to, asking for exactly these scopes. */
    String authorizeUrl(String state, Set<String> scopes);

    /** Exchanges the authorization code and validates the access token that comes back. */
    SsoLink exchange(String code);

    /**
     * Revokes a refresh token at EVE, which ends the whole grant it carries. True when EVE confirmed. A failure is not
     * thrown: callers delete their own copy either way and tell the user to revoke it at CCP's page.
     */
    boolean revoke(String refreshToken);

    /** What a validated link gives: who the character is, what it granted, and the refresh token to store. */
    record SsoLink(VerifiedToken identity, String refreshToken) {
    }

    /** The claims of a validated access token. {@code ownerHash} changes when the character is sold. */
    record VerifiedToken(long characterId, String name, String ownerHash, Set<String> scopes) {
    }

    /** The token did not pass validation, or SSO refused the code. The message never carries a token. */
    class SsoException extends RuntimeException {
        SsoException(String message) {
            super(message);
        }

        SsoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
