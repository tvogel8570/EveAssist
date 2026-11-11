package com.eveassist.characters.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.kms.KmsClient;

@Configuration
@EnableConfigurationProperties(EveSsoProperties.class)
class CharactersConfig {

    @Bean
    DataKeyProvider dataKeyProvider(Environment environment,
            @Value("${eveassist.characters.token-data-key:}") String envKey,
            @Value("${eveassist.characters.token-data-key-wrapped:}") String wrappedKey,
            @Value("${eveassist.characters.kms-key-id:}") String kmsKeyId) {
        return DataKeyProviders.choose(envKey, wrappedKey, kmsKeyId, environment.acceptsProfiles(Profiles.of("prod")),
                // credentials from the instance role, the region from AWS_REGION
                () -> KmsClient.builder().httpClient(UrlConnectionHttpClient.create()).build());
    }

    @Bean
    TokenCipher tokenCipher(DataKeyProvider keys) {
        return new TokenCipher(keys);
    }
}
