package com.eveassist.characters.internal;

import javax.crypto.SecretKey;

/** Where the data key that encrypts refresh tokens comes from: the environment in dev, a KMS-wrapped key later. */
interface DataKeyProvider {

    /** Stored with each token, so that a token can be decrypted after the key is rotated. */
    String keyId();

    SecretKey key(String keyId);
}
