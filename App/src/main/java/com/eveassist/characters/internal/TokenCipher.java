package com.eveassist.characters.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM with a random 96-bit nonce per token. The character id and key id are the associated data, so a
 * ciphertext copied to another character's row does not decrypt. Failures never carry the token.
 */
class TokenCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final DataKeyProvider keys;
    private final SecureRandom random = new SecureRandom();

    TokenCipher(DataKeyProvider keys) {
        this.keys = keys;
    }

    EncryptedToken encrypt(long eveCharacterId, String refreshToken) {
        var keyId = keys.keyId();
        var nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.key(keyId), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData(eveCharacterId, keyId));
            return new EncryptedToken(cipher.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8)), nonce, keyId);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a refresh token");
        }
    }

    String decrypt(long eveCharacterId, EncryptedToken stored) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.key(stored.keyId()), new GCMParameterSpec(TAG_BITS, stored.nonce()));
            cipher.updateAAD(associatedData(eveCharacterId, stored.keyId()));
            return new String(cipher.doFinal(stored.ciphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt a refresh token for character " + eveCharacterId);
        }
    }

    private static byte[] associatedData(long eveCharacterId, String keyId) {
        return (eveCharacterId + ":" + keyId).getBytes(StandardCharsets.UTF_8);
    }
}
