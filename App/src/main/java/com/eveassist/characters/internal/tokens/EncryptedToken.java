package com.eveassist.characters.internal.tokens;

/** A refresh token as stored: ciphertext with its 96-bit nonce and the id of the key that encrypted it. */
public record EncryptedToken(byte[] ciphertext, byte[] nonce, String keyId) {
}
