package com.eveassist.api.user;

import com.eveassist.api.user.impl.EveAssistUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig(EveAssistUserServiceImpl.class)
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
class EveAssistUserServiceTest {
    @MockBean
    private EveAssistUserRepository repo;
    @Autowired
    private EveAssistUserServiceImpl cut;
    
    @Test
    void whenUserNotFound_thenThrowUsernameNotFoundException() {
        when(repo.findByEmailIgnoreCase(anyString())).thenReturn(null);
        assertThatThrownBy(() -> cut.loadUserByUsername("missinguser@test.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
