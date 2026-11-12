package com.eveassist.characters.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;

/**
 * The prod data key: 32 random bytes that AWS KMS wrapped (see documentation/runbooks/initial-deployment.md for the
 * command). KMS is asked to unwrap it once, here, and the key then stays in memory; nothing but the wrapped form is in
 * the environment or in the database. A start-up that cannot reach KMS fails, so a mistake shows at once.
 */
final class KmsDataKeyProvider implements DataKeyProvider {

    /**
     * Bound to the wrapped key when it is made and checked when it is unwrapped, so a wrapped key made for something
     * else does not unwrap here. It also appears in CloudTrail, which says what a Decrypt call was for.
     */
    static final Map<String, String> ENCRYPTION_CONTEXT = Map.of("purpose", "eveassist-refresh-tokens");

    private final String keyId;
    private final SecretKey key;

    private KmsDataKeyProvider(String keyId, SecretKey key) {
        this.keyId = keyId;
        this.key = key;
    }

    /**
     * @param kms the client to unwrap with; the caller closes it
     * @param kmsKeyId the KMS key (id, ARN or alias) the data key must have been wrapped by
     * @param wrappedBase64 the wrapped data key, base64
     */
    static KmsDataKeyProvider unwrap(KmsClient kms, String kmsKeyId, String wrappedBase64) {
        byte[] wrapped;
        try {
            wrapped = Base64.getDecoder().decode(wrappedBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("eveassist.characters.token-data-key-wrapped is not base64");
        }
        byte[] plain = null;
        try {
            plain = kms.decrypt(DecryptRequest.builder()
                    .keyId(kmsKeyId)
                    .ciphertextBlob(SdkBytes.fromByteArray(wrapped))
                    .encryptionContext(ENCRYPTION_CONTEXT)
                    .build()).plaintext().asByteArray();
            if (plain.length != 32) {
                throw new IllegalStateException("The unwrapped token data key must be 32 bytes (AES-256)");
            }
            return new KmsDataKeyProvider(idOf(wrapped), new SecretKeySpec(plain, "AES"));
        } catch (SdkException e) {
            // the message names the failing call and says why (access denied, no region, ...), and holds no key material
            throw new IllegalStateException("KMS could not unwrap the token data key: " + e.getMessage(), e);
        } finally {
            if (plain != null) {
                Arrays.fill(plain, (byte) 0);
            }
        }
    }

    /** Names the key by its wrapped form, so the id is the same at every start and needs no setting of its own. */
    static String idOf(byte[] wrapped) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(wrapped);
            return "kms-" + HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String keyId() {
        return keyId;
    }

    @Override
    public SecretKey key(String keyId) {
        if (!this.keyId.equals(keyId)) {
            throw new IllegalStateException("No data key with id " + keyId);
        }
        return key;
    }
}
