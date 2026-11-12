package com.eveassist.characters.internal;

/** A refresh token as stored: ciphertext with its 96-bit nonce and the id of the key that encrypted it. */
record EncryptedToken(byte[] ciphertext, byte[] nonce, String keyId) {
}
