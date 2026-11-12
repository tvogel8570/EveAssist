package com.eveassist.characters.internal;

import java.time.Instant;
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
        @Override
        public String toString() {
            return "SsoLink[" + identity + ", refresh token hidden]";
        }
    }

    /**
     * Trades a refresh token for a new access token, which is validated like the one from linking. EVE may answer
     * with a new refresh token, and then the caller must keep that one. Throws {@link InvalidGrantException} when EVE
     * refuses the refresh token, and {@link SsoException} when it cannot be reached or answers badly.
     */
    RefreshedAccess refresh(String refreshToken);

    /** A new access token (a secret, never logged) with its validated claims, and the newest refresh token. */
    record RefreshedAccess(String accessToken, VerifiedToken identity, String refreshToken) {
        @Override
        public String toString() {
            return "RefreshedAccess[tokens hidden]";
        }
    }

    /**
     * The claims of a validated access token. {@code ownerHash} changes when the character is sold.
     *
     * @param expiresAt when this access token stops working
     */
    record VerifiedToken(long characterId, String name, String ownerHash, Set<String> scopes, Instant expiresAt) {
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

    /** EVE refused the refresh token: the player revoked the access, or it was revoked here. Only linking again helps. */
    class InvalidGrantException extends SsoException {
        InvalidGrantException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
