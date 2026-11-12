package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.KmsException;

class KmsDataKeyProviderTest {

    private static final byte[] DATA_KEY = bytes(32, 7);
    private static final byte[] WRAPPED = bytes(184, 3);
    private static final String WRAPPED_BASE64 = Base64.getEncoder().encodeToString(WRAPPED);

    private final KmsClient kms = mock(KmsClient.class);

    private static byte[] bytes(int length, int value) {
        var bytes = new byte[length];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private void kmsReturns(byte[] plaintext) {
        when(kms.decrypt(any(DecryptRequest.class)))
                .thenReturn(DecryptResponse.builder().plaintext(SdkBytes.fromByteArray(plaintext)).build());
    }

    @Test
    void unwrapsTheKeyOnceAndHoldsIt() {
        kmsReturns(DATA_KEY);

        var provider = KmsDataKeyProvider.unwrap(kms, "alias/eveassist-token-key", WRAPPED_BASE64);

        assertThat(provider.key(provider.keyId()).getEncoded()).isEqualTo(DATA_KEY);
        assertThat(provider.key(provider.keyId()).getAlgorithm()).isEqualTo("AES");
        verify(kms).decrypt(any(DecryptRequest.class)); // once, not per token
    }

    @Test
    void asksKmsForTheWrappedBytesWithTheKeyAndThePurpose() {
        kmsReturns(DATA_KEY);

        KmsDataKeyProvider.unwrap(kms, "alias/eveassist-token-key", WRAPPED_BASE64);

        var request = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(kms).decrypt(request.capture());
        assertThat(request.getValue().keyId()).isEqualTo("alias/eveassist-token-key");
        assertThat(request.getValue().ciphertextBlob().asByteArray()).isEqualTo(WRAPPED);
        assertThat(request.getValue().encryptionContext()).containsEntry("purpose", "eveassist-refresh-tokens");
    }

    @Test
    void theKeyIdIsTheSameAtEveryStartAndDiffersPerWrappedKey() {
        kmsReturns(DATA_KEY);
        var first = KmsDataKeyProvider.unwrap(kms, "k", WRAPPED_BASE64).keyId();
        var again = KmsDataKeyProvider.unwrap(kms, "k", WRAPPED_BASE64).keyId();
        var other = KmsDataKeyProvider.unwrap(kms, "k", Base64.getEncoder().encodeToString(bytes(184, 4))).keyId();

        assertThat(first).isEqualTo(again).startsWith("kms-").isNotEqualTo(other);
    }

    @Test
    void aTokenEncryptedWithItDecryptsAfterARestart() {
        kmsReturns(DATA_KEY);
        var cipher = new TokenCipher(KmsDataKeyProvider.unwrap(kms, "k", WRAPPED_BASE64));
        var stored = cipher.encrypt(90000001L, "refresh-token-value");

        var afterRestart = new TokenCipher(KmsDataKeyProvider.unwrap(kms, "k", WRAPPED_BASE64));

        assertThat(afterRestart.decrypt(90000001L, stored)).isEqualTo("refresh-token-value");
    }

    @Test
    void refusesAnotherKeyId() {
        kmsReturns(DATA_KEY);
        var provider = KmsDataKeyProvider.unwrap(kms, "k", WRAPPED_BASE64);

        assertThatThrownBy(() -> provider.key("env-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsAtStartWhenKmsRefuses() {
        when(kms.decrypt(any(DecryptRequest.class)))
                .thenThrow(KmsException.builder().message("User is not authorized to perform kms:Decrypt").build());

        assertThatThrownBy(() -> KmsDataKeyProvider.unwrap(kms, "k", WRAPPED_BASE64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KMS could not unwrap")
                .hasMessageContaining("not authorized")
                .hasMessageNotContaining(WRAPPED_BASE64);
    }

    @Test
    void failsWhenTheUnwrappedKeyIsNot32Bytes() {
        kmsReturns(bytes(16, 1));

        assertThatThrownBy(() -> KmsDataKeyProvider.unwrap(kms, "k", WRAPPED_BASE64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void failsWhenTheWrappedKeyIsNotBase64() {
        assertThatThrownBy(() -> KmsDataKeyProvider.unwrap(kms, "k", "not base64 !!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not base64");
    }
}
