package com.eveassist.characters.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ends a stored refresh token at EVE. Deleting our copy alone would leave the token valid, so removing a character,
 * linking it again and dropping its permissions all revoke the old token. It never throws, and never logs the token.
 */
@Component
class TokenRevoker {

    private static final Logger log = LoggerFactory.getLogger(TokenRevoker.class);

    private final EveSso sso;
    private final TokenCipher cipher;

    TokenRevoker(EveSso sso, TokenCipher cipher) {
        this.sso = sso;
        this.cipher = cipher;
    }

    /** True when EVE confirmed the revocation. */
    boolean revoke(long eveCharacterId, EncryptedToken stored) {
        String token;
        try {
            token = cipher.decrypt(eveCharacterId, stored);
        } catch (IllegalStateException e) {
            log.warn("A stored token for character {} could not be decrypted, so it was not revoked", eveCharacterId);
            return false;
        }
        return sso.revoke(token);
    }
}
