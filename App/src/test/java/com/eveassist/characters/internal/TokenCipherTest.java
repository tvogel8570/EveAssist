package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final TokenCipher cipher = new TokenCipher(new EnvDataKeyProvider(KEY));

    @Test
    void decryptsWhatItEncrypted() {
        var stored = cipher.encrypt(90000001L, "refresh-token-value");

        assertThat(cipher.decrypt(90000001L, stored)).isEqualTo("refresh-token-value");
        assertThat(stored.keyId()).isEqualTo(EnvDataKeyProvider.KEY_ID);
        assertThat(new String(stored.ciphertext())).doesNotContain("refresh-token-value");
    }

    @Test
    void usesANewNonceEachTime() {
        var first = cipher.encrypt(90000001L, "same");
        var second = cipher.encrypt(90000001L, "same");

        assertThat(first.nonce()).hasSize(12).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void aCiphertextCopiedToAnotherCharacterDoesNotDecrypt() {
        var stored = cipher.encrypt(90000001L, "secret-token");

        assertThatThrownBy(() -> cipher.decrypt(90000002L, stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("secret-token");
    }

    @Test
    void aTamperedCiphertextDoesNotDecrypt() {
        var stored = cipher.encrypt(90000001L, "secret-token");
        var tampered = Arrays.copyOf(stored.ciphertext(), stored.ciphertext().length);
        tampered[0] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(90000001L, new EncryptedToken(tampered, stored.nonce(), stored.keyId())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesAKeyOfTheWrongLength() {
        assertThatThrownBy(() -> new EnvDataKeyProvider(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
