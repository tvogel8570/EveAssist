package com.eveassist.client.user;

import com.eveassist.client.user.dto.EveAssistUserListDto;
import com.eveassist.client.user.entity.EveAssistUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:fixture/user.sql")
class EveAssistUserRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    EveAssistUserRepository cut;
    private final LocalDateTime testTime = LocalDateTime.now();


    @Test
    void shouldRetrieveCorrectTime() {
        var unique = UUID.randomUUID();
        EveAssistUser eveAssistUser = EveAssistUser.builder()
                .uniqueUser(unique)
                .screenName("screen")
                .createTimestamp(testTime)
                .updateTimestamp(testTime)
                .email("tim@test.com").build();
        cut.save(eveAssistUser);

        EveAssistUser retrievedUser = cut.findByUniqueUser(unique);
        assertThat(retrievedUser).isNotNull();
        assertThat(retrievedUser.getUniqueUser()).isEqualTo(unique);
        assertThat(retrievedUser.getCreateTimestamp()).isEqualToIgnoringSeconds(testTime);
    }

    @Test
    void shouldReturnListOfAllUsers() {
        List<EveAssistUserListDto> allUserInfo = cut.getAllUsersList();
        assertThat(allUserInfo).isNotNull().hasSize(2);
    }

    @Test
    void shouldReturnUserDto() {
        List<EveAssistUserListDto> userInfo = cut.getAllUsersList();
        assertThat(userInfo).isNotNull().hasSize(2);
    }


    @Test
    void shouldFindEmailCaseInsensitive() {
        EveAssistUser user = cut.findByEmailIgnoreCase("TEST@TEST.com");
        assertThat(user).isNotNull();
    }

    @Test
    void shouldThrowUserNotFoundException_whenSearchForEmailFails() {
        EveAssistUser missingUser = cut.findByEmailIgnoreCase("asdf@asdf.com");
        assertThat(missingUser).isNull();
    }
}