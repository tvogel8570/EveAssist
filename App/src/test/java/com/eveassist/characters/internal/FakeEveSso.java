package com.eveassist.characters.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** EVE SSO as a test sees it: records what was asked, and answers with what the test set up. */
class FakeEveSso implements EveSso {

    Set<String> askedScopes;
    String askedState;
    int exchanges;
    SsoLink nextLink;
    SsoException failWith;
    /** The refresh tokens EVE was asked to revoke, in order. */
    final List<String> revoked = new ArrayList<>();
    boolean revokeConfirmed = true;

    void reset() {
        revoked.clear();
        revokeConfirmed = true;
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
    public boolean revoke(String refreshToken) {
        revoked.add(refreshToken);
        return revokeConfirmed;
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
