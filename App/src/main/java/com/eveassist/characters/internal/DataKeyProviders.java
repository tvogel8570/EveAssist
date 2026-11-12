package com.eveassist.characters.internal;

import java.util.function.Supplier;
import software.amazon.awssdk.services.kms.KmsClient;

/** Decides where the token data key comes from, so that a wrong combination fails at start-up. */
final class DataKeyProviders {

    private DataKeyProviders() {
    }

    /**
     * @param envKey the dev key (32 bytes, base64), or blank
     * @param wrappedKey the KMS-wrapped key, or blank
     * @param kmsKeyId the KMS key that wrapped it, or blank
     * @param prod whether the prod profile is active: there the key is never taken from the environment, because a plain
     *             key beside the application is what a breach would reveal
     * @param kms makes the KMS client; called only when a wrapped key is set, and the client is closed after the unwrap
     */
    static DataKeyProvider choose(String envKey, String wrappedKey, String kmsKeyId, boolean prod,
            Supplier<KmsClient> kms) {
        boolean hasEnv = !envKey.isBlank();
        boolean hasWrapped = !wrappedKey.isBlank();
        if (hasEnv && hasWrapped) {
            throw new IllegalStateException(
                    "Set EVE_TOKEN_DATA_KEY (dev) or EVE_TOKEN_DATA_KEY_WRAPPED (prod), not both");
        }
        if (hasWrapped) {
            if (kmsKeyId.isBlank()) {
                throw new IllegalStateException("Set EVE_KMS_KEY_ID to the KMS key that wrapped EVE_TOKEN_DATA_KEY_WRAPPED");
            }
            try (var client = kms.get()) {
                return KmsDataKeyProvider.unwrap(client, kmsKeyId, wrappedKey);
            }
        }
        if (hasEnv) {
            if (prod) {
                throw new IllegalStateException(
                        "The prod profile takes the token data key from KMS (EVE_TOKEN_DATA_KEY_WRAPPED), not from EVE_TOKEN_DATA_KEY");
            }
            return new EnvDataKeyProvider(envKey);
        }
        throw new IllegalStateException(
                "Set EVE_TOKEN_DATA_KEY (dev: 32 random bytes in base64, for example from `openssl rand -base64 32`) "
                        + "or EVE_TOKEN_DATA_KEY_WRAPPED with EVE_KMS_KEY_ID (prod)");
    }
}
