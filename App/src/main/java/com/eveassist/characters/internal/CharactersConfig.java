package com.eveassist.characters.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EveSsoProperties.class)
class CharactersConfig {

    @Bean
    DataKeyProvider dataKeyProvider(@Value("${eveassist.characters.token-data-key:}") String base64Key) {
        if (base64Key.isBlank()) {
            throw new IllegalStateException(
                    "Set EVE_TOKEN_DATA_KEY (32 random bytes in base64, for example from `openssl rand -base64 32`)");
        }
        return new EnvDataKeyProvider(base64Key);
    }

    @Bean
    TokenCipher tokenCipher(DataKeyProvider keys) {
        return new TokenCipher(keys);
    }
}
