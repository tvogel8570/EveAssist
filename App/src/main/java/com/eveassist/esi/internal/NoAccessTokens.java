package com.eveassist.esi.internal;

import com.eveassist.esi.AccessTokens;
import com.eveassist.esi.TokenGrant;

/** For a client with no characters to ask about, such as one built in a test of the public routes. */
final class NoAccessTokens implements AccessTokens {

    @Override
    public TokenGrant accessToken(long eveCharacterId, String requiredScope) {
        return TokenGrant.unavailable(TokenGrant.Problem.UNAVAILABLE);
    }

    @Override
    public void invalidate(long eveCharacterId) {
    }
}
