package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Tokens are signed here with a key of our own, in place of EVE SSO's. */
class EveTokenValidatorTest {

    private static final String CLIENT_ID = "our-client";

    private static KeyPair keys;
    private static KeyPair otherKeys;

    private final EveTokenValidator validator = new EveTokenValidator(
            () -> NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build(), CLIENT_ID);

    @BeforeAll
    static void generateKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    private static JWTClaimsSet.Builder goodClaims() {
        return new JWTClaimsSet.Builder()
                .issuer("https://login.eveonline.com/")
                .subject("CHARACTER:EVE:90000001")
                .audience(List.of(CLIENT_ID, "EVE Online"))
                .expirationTime(Date.from(Instant.now().plusSeconds(1200)))
                .claim("name", "Alt One")
                .claim("owner", "owner-hash-1")
                .claim("scp", List.of("esi-assets.read_assets.v1", "esi-universe.read_structures.v1"));
    }

    private static String sign(JWTClaimsSet claims, KeyPair signer) throws Exception {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate()));
        return jwt.serialize();
    }

    @Test
    void readsTheCharacterAndItsScopesFromAGoodToken() throws Exception {
        var token = validator.validate(sign(goodClaims().build(), keys));

        assertThat(token.characterId()).isEqualTo(90000001L);
        assertThat(token.name()).isEqualTo("Alt One");
        assertThat(token.ownerHash()).isEqualTo("owner-hash-1");
        assertThat(token.scopes()).containsExactlyInAnyOrder("esi-assets.read_assets.v1",
                "esi-universe.read_structures.v1");
    }

    @Test
    void acceptsTheIssuerWithoutTheScheme() throws Exception {
        assertThat(validator.validate(sign(goodClaims().issuer("login.eveonline.com").build(), keys)).characterId())
                .isEqualTo(90000001L);
    }

    @Test
    void acceptsASingleScopeSentAsAString() throws Exception {
        var token = validator.validate(sign(goodClaims().claim("scp", "esi-assets.read_assets.v1").build(), keys));

        assertThat(token.scopes()).containsExactly("esi-assets.read_assets.v1");
    }

    @Test
    void refusesATokenSignedWithAnotherKey() throws Exception {
        assertThatThrownBy(() -> validator.validate(sign(goodClaims().build(), otherKeys)))
                .isInstanceOf(EveSso.SsoException.class);
    }

    @Test
    void refusesAnExpiredToken() throws Exception {
        var expired = goodClaims().expirationTime(Date.from(Instant.now().minusSeconds(600))).build();

        assertThatThrownBy(() -> validator.validate(sign(expired, keys))).isInstanceOf(EveSso.SsoException.class);
    }

    @Test
    void refusesAWrongIssuer() throws Exception {
        assertThatThrownBy(() -> validator.validate(sign(goodClaims().issuer("https://evil.example/").build(), keys)))
                .isInstanceOf(EveSso.SsoException.class).hasMessageContaining("issuer");
    }

    @Test
    void refusesATokenForAnotherApplicationOrWithoutTheEveAudience() throws Exception {
        var otherApp = goodClaims().audience(List.of("someone-else", "EVE Online")).build();
        var noEve = goodClaims().audience(List.of(CLIENT_ID)).build();

        assertThatThrownBy(() -> validator.validate(sign(otherApp, keys))).isInstanceOf(EveSso.SsoException.class);
        assertThatThrownBy(() -> validator.validate(sign(noEve, keys))).isInstanceOf(EveSso.SsoException.class);
    }

    @Test
    void refusesATokenThatIsNotForACharacter() throws Exception {
        assertThatThrownBy(() -> validator.validate(sign(goodClaims().subject("CORPORATION:EVE:1").build(), keys)))
                .isInstanceOf(EveSso.SsoException.class);
    }
}
