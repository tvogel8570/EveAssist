package com.eveassist.client.user.impl;

import com.eveassist.client.core.exception.EaBusinessException;
import com.eveassist.client.user.EveAssistUserService;
import com.eveassist.client.user.dto.EveAssistUserDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test-containers-flyway")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EveAssistUserServiceImplIT {
    @Autowired
    private EveAssistUserService cut;

    @Test
    void contextLoads() {
        assertThat(cut).isNotNull();
    }

    @Test
    void givenInvalidUserDto_whenSave_thenException() {
        UUID userUnique = UUID.randomUUID();
        String userName = "user";
        String email = "user@nosuch.com";

        EveAssistUserDto built = EveAssistUserDto.EveAssistUserDtoBuilder.anEveAssistUserDto()
                .withUniqueUser(userUnique)
                .withUserName(userName)
                .withEmail(email)
                .build();
        assertThatThrownBy(() -> cut.addUser(built)).isInstanceOf(EaBusinessException.class);
    }
}