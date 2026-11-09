package com.eveassist.characters.internal;

import java.util.Set;

/** EVE SSO as a test sees it: records what was asked, and answers with what the test set up. */
class FakeEveSso implements EveSso {

    Set<String> askedScopes;
    String askedState;
    int exchanges;
    SsoLink nextLink;
    SsoException failWith;

    void reset() {
        askedScopes = null;
        askedState = null;
        exchanges = 0;
        nextLink = null;
        failWith = null;
    }

    SsoLink link(long characterId, String name, String refreshToken) {
        return new SsoLink(new VerifiedToken(characterId, name, "owner-hash-" + characterId,
                Set.of("esi-assets.read_assets.v1", "esi-universe.read_structures.v1")), refreshToken);
    }

    @Override
    public String authorizeUrl(String state, Set<String> scopes) {
        askedState = state;
        askedScopes = scopes;
        return "https://sso.invalid/authorize?state=" + state;
    }

    @Override
    public SsoLink exchange(String code) {
        exchanges++;
        if (failWith != null) {
            throw failWith;
        }
        return nextLink;
    }
}
