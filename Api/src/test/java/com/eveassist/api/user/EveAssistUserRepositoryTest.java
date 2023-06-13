package com.eveassist.api.user;

import com.eveassist.api.user.dto.EveAssistUserListDto;
import com.eveassist.api.user.entity.EveAssistUser;
import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.Length;
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
    private final String testUserUnique = "000000000000000000000000000001";

    @Test
    void shouldRetrieveCorrectTime() {
        EveAssistUser eveAssistUser = EveAssistUser.builder()
                .uniqueUser("123456789012345678901234567890")
                .screenName("me")
                .password("secret")
                .createTimestamp(testTime)
                .updateTimestamp(testTime)
                .email("tim@test.com").build();
        cut.save(eveAssistUser);

        @Length(min = 30, max = 30) @NotEmpty String uniqueUser = "123456789012345678901234567890";
        EveAssistUser retrievedUser = cut.findByUniqueUser(uniqueUser);
        assertThat(retrievedUser).isNotNull();
        assertThat(retrievedUser.getUniqueUser()).isEqualTo(uniqueUser);
        assertThat(retrievedUser.getCreateTimestamp()).isEqualTo(testTime);
    }

    @Test
    void shouldReturnListOfAllUsers() {
        List<EveAssistUserListDto> allUserInfo = cut.getAllUsersList();
        assertThat(allUserInfo).isNotNull();
        assertThat(allUserInfo.size()).isEqualTo(2);
    }

    @Test
    void shouldReturnUserDto() {
        List<EveAssistUserListDto> userInfo = cut.getAllUsersList();
        assertThat(userInfo).isNotNull();
        assertThat(userInfo.size()).isEqualTo(2);
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