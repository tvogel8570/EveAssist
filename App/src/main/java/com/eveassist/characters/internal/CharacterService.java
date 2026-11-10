package com.eveassist.characters.internal;

import com.eveassist.characters.Feature;
import com.eveassist.identity.Viewer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** What the Characters page does: list a viewer's characters, turn features on and off, remove, show a portrait. */
@Service
public class CharacterService {

    /** What became of a character's saved access. EVE confirms a revocation, or the page says to revoke it by hand. */
    public enum AccessResult {
        /** The character is not the viewer's, or does not exist. */
        NOT_FOUND,
        /** Done, and EVE confirmed that the old token is revoked (or there was no token to revoke). */
        DONE,
        /** Done on our side, but EVE did not confirm the revocation, so the old token may still be valid. */
        DONE_NOT_REVOKED
    }

    private final CharacterRepository characters;
    private final FeatureRepository features;
    private final TokenRevoker revoker;
    private final CharacterAccessTokens accessTokens;
    private final MembershipRepository memberships;

    /** Scopes that give nothing to a character that is not a Director of its corporation. */
    private static final Set<String> DIRECTOR_ONLY = Set.of("esi-assets.read_corporation_assets.v1",
            "esi-corporations.read_divisions.v1");

    CharacterService(CharacterRepository characters, FeatureRepository features, TokenRevoker revoker,
            CharacterAccessTokens accessTokens, MembershipRepository memberships) {
        this.memberships = memberships;
        this.accessTokens = accessTokens;
        this.characters = characters;
        this.features = features;
        this.revoker = revoker;
    }

    public List<CharacterView> list(Viewer viewer) {
        var needed = neededScopes(features.turnedOn(viewer.accountId()));
        var all = characters.listFor(viewer.accountId());
        var known = memberships.findAll(all.stream().map(CharacterDetails::id).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(MembershipRepository.Membership::characterId,
                        MembershipRepository.Membership::director));
        return all.stream().map(details -> {
            var missing = new TreeSet<>(needed);
            missing.removeAll(details.scopes());
            var unused = new TreeSet<>(details.scopes());
            unused.removeAll(needed);
            var inUse = new TreeSet<>(details.scopes());
            inUse.removeAll(unused);
            var status = "NEEDS_REAUTHORIZATION".equals(details.state()) ? CharacterView.Status.REAUTHORIZE
                    : missing.isEmpty() ? CharacterView.Status.ACTIVE : CharacterView.Status.NEEDS_PERMISSIONS;
            return new CharacterView(details.id(), details.name(), status,
                    List.copyOf(new TreeSet<>(details.scopes())), List.copyOf(inUse), List.copyOf(missing),
                    List.copyOf(unused), details.hasPortrait(),
                    Boolean.FALSE.equals(known.get(details.id()))
                            ? inUse.stream().filter(DIRECTOR_ONLY::contains).toList() : List.of());
        }).toList();
    }

    public Set<Feature> featuresOn(Viewer viewer) {
        return features.turnedOn(viewer.accountId());
    }

    /**
     * Turns a feature on or off. A feature that builds on another turns that one on with it, and turning a feature off
     * turns off the features that build on it, so the account never has one on without what it needs.
     */
    public void setFeature(Viewer viewer, Feature feature, boolean on) {
        if (on) {
            if (feature.requires() != null) {
                features.turnOn(viewer.accountId(), feature.requires());
            }
            features.turnOn(viewer.accountId(), feature);
        } else {
            features.turnOff(viewer.accountId(), feature);
            for (var other : Feature.values()) {
                if (other.requires() == feature) {
                    features.turnOff(viewer.accountId(), other);
                }
            }
        }
    }

    /**
     * Removes the character and deletes its token at once, then revokes the token at EVE. The deletion does not wait
     * for EVE: when EVE does not answer, the result says so and the page tells the user how to revoke it by hand.
     */
    public AccessResult remove(Viewer viewer, UUID characterId) {
        var stored = characters.findOwnedToken(viewer.accountId(), characterId);
        if (!characters.remove(viewer.accountId(), characterId)) {
            return AccessResult.NOT_FOUND;
        }
        stored.ifPresent(token -> accessTokens.forget(token.eveCharacterId()));
        return stored.map(token -> revoker.revoke(token.eveCharacterId(), token.token())
                ? AccessResult.DONE : AccessResult.DONE_NOT_REVOKED).orElse(AccessResult.DONE);
    }

    /**
     * Drops the permissions a character granted: revokes its token at EVE and deletes ours. A token's scopes cannot be
     * narrowed, so the only way to keep fewer is to revoke it and link again. The character and its stored data stay,
     * marked as needing re-authorization.
     */
    public AccessResult dropAccess(Viewer viewer, UUID characterId) {
        var stored = characters.findOwnedToken(viewer.accountId(), characterId);
        if (!characters.dropAccess(viewer.accountId(), characterId)) {
            return AccessResult.NOT_FOUND;
        }
        stored.ifPresent(token -> accessTokens.forget(token.eveCharacterId()));
        return stored.map(token -> revoker.revoke(token.eveCharacterId(), token.token())
                ? AccessResult.DONE : AccessResult.DONE_NOT_REVOKED).orElse(AccessResult.DONE);
    }

    /** The portrait, only for the character's owner. */
    public Optional<byte[]> portrait(Viewer viewer, UUID characterId) {
        return characters.findPortrait(viewer.accountId(), characterId);
    }

    private static Set<String> neededScopes(Set<Feature> on) {
        var needed = new TreeSet<String>();
        on.forEach(feature -> needed.addAll(feature.scopes()));
        return needed;
    }
}
