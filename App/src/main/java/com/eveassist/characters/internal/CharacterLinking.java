package com.eveassist.characters.internal;

import com.eveassist.characters.Feature;
import com.eveassist.identity.Viewer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Links a character through EVE SSO, in the request that needs it (see identity-and-tokens.md, First build). The
 * caller keeps the {@code state} this hands out in the session and gives it back with the callback.
 */
@Service
public class CharacterLinking {

    public enum Result { LINKED, RENEWED, ON_ANOTHER_ACCOUNT, STATE_MISMATCH, REFUSED, NO_FEATURE_ON }

    /** Where to send the browser, and the state to keep until it comes back. */
    public record Start(String authorizeUrl, String state) {
    }

    private final EveSso sso;
    private final FeatureRepository features;
    private final CharacterRepository characters;
    private final TokenCipher cipher;
    private final SecureRandom random = new SecureRandom();

    CharacterLinking(EveSso sso, FeatureRepository features, CharacterRepository characters, TokenCipher cipher) {
        this.sso = sso;
        this.features = features;
        this.characters = characters;
        this.cipher = cipher;
    }

    /** Asks for the scopes of the turned-on features and nothing else. Empty when no feature is on. */
    public Optional<Start> start(Viewer viewer) {
        Set<String> scopes = new TreeSet<>();
        features.turnedOn(viewer.accountId()).forEach(feature -> scopes.addAll(feature.scopes()));
        if (scopes.isEmpty()) {
            return Optional.empty();
        }
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return Optional.of(new Start(sso.authorizeUrl(state, scopes), state));
    }

    public Result complete(Viewer viewer, String expectedState, String state, String code) {
        if (expectedState == null || state == null
                || !MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8),
                        state.getBytes(StandardCharsets.UTF_8))) {
            return Result.STATE_MISMATCH;
        }
        EveSso.SsoLink link;
        try {
            link = sso.exchange(code);
        } catch (EveSso.SsoException e) {
            return Result.REFUSED;
        }
        var identity = link.identity();
        var outcome = characters.link(viewer.accountId(), identity.characterId(), identity.name(),
                identity.ownerHash(), identity.scopes(), cipher.encrypt(identity.characterId(), link.refreshToken()));
        return switch (outcome) {
            case LINKED -> Result.LINKED;
            case RENEWED -> Result.RENEWED;
            case ON_ANOTHER_ACCOUNT -> Result.ON_ANOTHER_ACCOUNT;
        };
    }

    public Set<Feature> featuresOn(Viewer viewer) {
        return features.turnedOn(viewer.accountId());
    }
}
