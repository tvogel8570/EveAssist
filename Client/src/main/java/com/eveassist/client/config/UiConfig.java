package com.eveassist.client.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

@Configuration
public class UiConfig {

    @Value("${api-host}")
    String apiBaseUri;

    @Value("${keycloak-base-url}")
    String keycloakBaseUri;

    @Bean
    public ITemplateResolver svgTemplateResolver() {
        SpringResourceTemplateResolver resolver = new
                SpringResourceTemplateResolver();
        resolver.setPrefix("classpath:/templates/svg/");
        resolver.setSuffix(".svg");
        resolver.setTemplateMode("XML");
        return resolver;
    }

    @Bean
    @Qualifier("EveAssistApi")
    public RestClient eaApiRestClient() {
        return RestClient.create(apiBaseUri);
    }

    @Bean
    @Qualifier("keycloak")
    public RestClient keycloakRestClient() {
        return RestClient.create(keycloakBaseUri);
    }
}
