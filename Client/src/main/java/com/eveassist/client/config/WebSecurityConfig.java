package com.eveassist.client.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {
    @Bean
    SecurityFilterChain
    clientSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http.oauth2Login(withDefaults());
        http.oauth2Client(withDefaults());
        http.logout(withDefaults());
        // @formatter:off
        http.authorizeHttpRequests(ex -> ex
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .requestMatchers("/", "/login/**", "/oauth2/**").permitAll()
                .anyRequest().authenticated());
        // @formatter:on
        return http.build();
    }
}
