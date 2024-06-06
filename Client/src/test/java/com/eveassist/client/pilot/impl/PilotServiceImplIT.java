package com.eveassist.client.pilot.impl;

import com.eveassist.client.pilot.PilotMapper;
import com.eveassist.client.pilot.PilotRepository;
import com.eveassist.client.pilot.PilotService;
import com.eveassist.client.user.EveAssistUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest
@ActiveProfiles("test-containers-flyway")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PilotServiceImplIT {
    @Autowired
    PilotService cut;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    PilotRepository pilotRepository;
    @Autowired
    RestClient eaApiRestClient;
    @Autowired
    PilotMapper pilotMapper;
    @Autowired
    EveAssistUserRepository eveAssistUserRepository;

    @Test
    void contextLoads() {
        Assertions.assertThat(cut).isNotNull();
    }


}