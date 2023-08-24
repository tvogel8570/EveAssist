package com.eveassist.client.user;

import com.eveassist.client.user.impl.EveAssistUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig(EveAssistUserServiceImpl.class)
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
class EveAssistUserServiceTest {
    @MockBean
    private EveAssistUserRepository repo;
    @Autowired
    private EveAssistUserServiceImpl cut;

    @Test
    void contextLoads() {
        assertThat(cut).isNotNull();
    }
}
