package com.eveassist.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

@Configuration
public class UiConfig {
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
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
