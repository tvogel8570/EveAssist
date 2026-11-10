package com.eveassist.esi;

/**
 * Gives the ESI client an access token for a linked character. The {@code esi} module owns this port and the
 * {@code characters} module, which holds the refresh tokens, implements it, so that {@code esi} depends on nothing.
 */
@org.jmolecules.architecture.hexagonal.SecondaryPort
public interface AccessTokens {

    /**
     * A token that is valid now, refreshed shortly before it expires. When the character did not grant
     * {@code requiredScope}, no token is given and the call is not made, because ESI counts a 403 as an error.
     */
    TokenGrant accessToken(long eveCharacterId, String requiredScope);

    /** ESI answered 401 for this token: forget it, so that the next request gets a fresh one. */
    void invalidate(long eveCharacterId);
}
