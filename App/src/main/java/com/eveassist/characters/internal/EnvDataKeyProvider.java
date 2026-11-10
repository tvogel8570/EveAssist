package com.eveassist.characters.internal;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** The dev data key: 32 random bytes in base64, from {@code EVE_TOKEN_DATA_KEY}. Never logged. */
final class EnvDataKeyProvider implements DataKeyProvider {

    static final String KEY_ID = "env-1";

    private final SecretKey key;

    EnvDataKeyProvider(String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("eveassist.characters.token-data-key is not base64");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("eveassist.characters.token-data-key must be 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public String keyId() {
        return KEY_ID;
    }

    @Override
    public SecretKey key(String keyId) {
        if (!KEY_ID.equals(keyId)) {
            throw new IllegalStateException("No data key with id " + keyId);
        }
        return key;
    }
}
