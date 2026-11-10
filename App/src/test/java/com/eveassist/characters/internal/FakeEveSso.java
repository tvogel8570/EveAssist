package com.eveassist.characters.internal;

import java.time.Instant;
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
        nextRefresh = null;
        refreshFailure = null;
        refreshed.clear();
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
                Set.of("esi-assets.read_assets.v1", "esi-universe.read_structures.v1"),
                Instant.now().plusSeconds(1200)), refreshToken);
    }

    /** What a refresh gives next, or null to answer as the default: a new access token for the character. */
    RefreshedAccess nextRefresh;
    /** A refresh fails with this instead, when set. */
    SsoException refreshFailure;
    final List<String> refreshed = java.util.Collections.synchronizedList(new ArrayList<>());
    /** What a refresh with a given refresh token gives, for tests where many characters refresh at once. */
    final java.util.Map<String, RefreshedAccess> answers = new java.util.concurrent.ConcurrentHashMap<>();

    /** A refresh answer for a character, with the scopes it still holds. */
    RefreshedAccess access(long characterId, String ownerHash, Set<String> scopes, Instant expiresAt,
            String refreshToken) {
        return new RefreshedAccess("access-" + characterId + "-" + refreshed.size(),
                new VerifiedToken(characterId, "Name", ownerHash, scopes, expiresAt), refreshToken);
    }

    @Override
    public RefreshedAccess refresh(String refreshToken) {
        refreshed.add(refreshToken);
        var own = answers.get(refreshToken);
        if (own != null) {
            return own;
        }
        if (refreshFailure != null) {
            throw refreshFailure;
        }
        return nextRefresh;
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
