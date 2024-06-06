package com.eveassist.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(value = PilotProps.class)
public class PilotPropsTest {
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    PilotProps props;

    @Test
    void contextLoads() {
        assertThat(props).isNotNull();
        assertThat(props.scope()).isNotNull().hasSize(6);
        assertThat(props.redirectUri()).isNotNull().isEqualTo("https://localhost:9443/pilot/login");
    }
}