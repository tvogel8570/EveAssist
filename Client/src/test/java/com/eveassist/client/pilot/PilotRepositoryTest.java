package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotListInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Sql("classpath:fixture/pilot.sql")
@ActiveProfiles("test-containers-flyway")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PilotRepositoryTest {

    @Autowired
    PilotRepository cut;

    @Test
    void givenValidUserUUID_thenAllPilotsForThatUser() {

        List<PilotListInfo> pilots = cut.findByEveAssistUser_UniqueUserOrderByNameAsc(
                UUID.fromString("dd6f91d9-d931-461b-ac16-8b93ed9f0f77"), Pageable.ofSize(10));
        assertThat(pilots).hasSize(3);
        assertThat(pilots.get(0).getName()).isEqualTo("Cass");
    }
}