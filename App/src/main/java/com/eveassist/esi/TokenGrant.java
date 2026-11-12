package com.eveassist.esi;

/**
 * An access token, or the reason there is none. The token is a secret: never log it or put it in a message.
 *
 * @param token   the bearer token, or null when {@code problem} is set
 * @param problem why there is no token, or null
 */
public record TokenGrant(String token, Problem problem) {

    public enum Problem {
        /** EVE refused the saved access, or the character was sold. Only linking it again helps. */
        NEEDS_REAUTHORIZATION,
        /** The character did not grant the scope this call needs. */
        MISSING_SCOPE,
        /** EVE SSO could not be reached or answered badly. Trying again later may work. */
        UNAVAILABLE
    }

    public static TokenGrant of(String token) {
        return new TokenGrant(token, null);
    }

    public static TokenGrant unavailable(Problem problem) {
        return new TokenGrant(null, problem);
    }

    /** Hides the token from logs and exception messages. */
    @Override
    public String toString() {
        return token == null ? "TokenGrant[" + problem + "]" : "TokenGrant[token hidden]";
    }
}
