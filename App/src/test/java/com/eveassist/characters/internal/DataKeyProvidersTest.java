package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;

class DataKeyProvidersTest {

    private static final String ENV_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String WRAPPED = Base64.getEncoder().encodeToString(new byte[100]);

    private final KmsClient kms = mock(KmsClient.class);
    private final AtomicBoolean kmsAsked = new AtomicBoolean();

    private java.util.function.Supplier<KmsClient> kms() {
        when(kms.decrypt(any(DecryptRequest.class)))
                .thenReturn(DecryptResponse.builder().plaintext(SdkBytes.fromByteArray(new byte[32])).build());
        return () -> {
            kmsAsked.set(true);
            return kms;
        };
    }

    private java.util.function.Supplier<KmsClient> noKms() {
        return () -> {
            throw new AssertionError("KMS must not be used");
        };
    }

    @Test
    void devTakesTheKeyFromTheEnvironmentWithoutKms() {
        var provider = DataKeyProviders.choose(ENV_KEY, "", "", false, noKms());

        assertThat(provider.keyId()).isEqualTo(EnvDataKeyProvider.KEY_ID);
    }

    @Test
    void aWrappedKeyIsUnwrappedByKmsAndTheClientIsClosed() {
        var provider = DataKeyProviders.choose("", WRAPPED, "alias/eveassist-token-key", true, kms());

        assertThat(provider.keyId()).startsWith("kms-");
        assertThat(kmsAsked).isTrue();
        verify(kms).close();
    }

    @Test
    void prodNeverTakesAPlainKeyFromTheEnvironment() {
        assertThatThrownBy(() -> DataKeyProviders.choose(ENV_KEY, "", "", true, noKms()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod profile");
    }

    @Test
    void bothKeysAreAMistake() {
        assertThatThrownBy(() -> DataKeyProviders.choose(ENV_KEY, WRAPPED, "k", false, noKms()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not both");
    }

    @Test
    void aWrappedKeyNeedsTheKmsKeyThatWrappedIt() {
        assertThatThrownBy(() -> DataKeyProviders.choose("", WRAPPED, " ", true, noKms()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EVE_KMS_KEY_ID");
    }

    @Test
    void noKeyAtAllIsAMistakeThatSaysWhatToSet() {
        assertThatThrownBy(() -> DataKeyProviders.choose("", "", "", false, noKms()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EVE_TOKEN_DATA_KEY")
                .hasMessageContaining("EVE_TOKEN_DATA_KEY_WRAPPED");
    }
}
