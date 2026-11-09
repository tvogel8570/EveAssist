package com.eveassist.characters.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Validates an EVE SSO access token as the EVE documentation says: signature and expiry (by the decoder), issuer
 * {@code https://login.eveonline.com/} or {@code login.eveonline.com}, and an audience that holds both our client id
 * and {@code "EVE Online"}. Knows nothing about HTTP, so it is tested with tokens signed in the test.
 */
class EveTokenValidator {

    private static final Set<String> ISSUERS = Set.of("https://login.eveonline.com/", "login.eveonline.com");
    private static final String SUBJECT_PREFIX = "CHARACTER:EVE:";

    private final Supplier<JwtDecoder> decoder;
    private final String clientId;

    EveTokenValidator(Supplier<JwtDecoder> decoder, String clientId) {
        this.decoder = decoder;
        this.clientId = clientId;
    }

    EveSso.VerifiedToken validate(String accessToken) {
        Jwt jwt;
        try {
            jwt = decoder.get().decode(accessToken);
        } catch (JwtException e) {
            throw new EveSso.SsoException("The access token did not validate", e);
        }
        var issuer = jwt.getClaimAsString("iss");
        if (issuer == null || !ISSUERS.contains(issuer)) {
            throw new EveSso.SsoException("The access token has the wrong issuer");
        }
        var audience = jwt.getAudience();
        if (audience == null || !audience.contains(clientId) || !audience.contains("EVE Online")) {
            throw new EveSso.SsoException("The access token is not for this application");
        }
        var subject = jwt.getSubject();
        if (subject == null || !subject.startsWith(SUBJECT_PREFIX)) {
            throw new EveSso.SsoException("The access token has no character");
        }
        long characterId;
        try {
            characterId = Long.parseLong(subject.substring(SUBJECT_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new EveSso.SsoException("The access token has no character");
        }
        var name = jwt.getClaimAsString("name");
        var ownerHash = jwt.getClaimAsString("owner");
        if (name == null || ownerHash == null) {
            throw new EveSso.SsoException("The access token lacks the character name or owner");
        }
        return new EveSso.VerifiedToken(characterId, name, ownerHash, scopes(jwt));
    }

    /** ESI sends {@code scp} as an array, or as a single string when only one scope was granted. */
    private static Set<String> scopes(Jwt jwt) {
        var scp = jwt.getClaim("scp");
        var result = new HashSet<String>();
        if (scp instanceof String single) {
            result.add(single);
        } else if (scp instanceof List<?> list) {
            list.forEach(scope -> result.add(String.valueOf(scope)));
        }
        return Set.copyOf(result);
    }
}
