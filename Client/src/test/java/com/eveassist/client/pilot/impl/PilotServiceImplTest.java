package com.eveassist.client.pilot.impl;

import com.eveassist.client.TestConfig;
import com.eveassist.client.pilot.PilotMapper;
import com.eveassist.client.pilot.PilotRepository;
import com.eveassist.client.user.EveAssistUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig({TestConfig.class})
class PilotServiceImplTest {
    @Autowired
    ObjectMapper mapper;    // bean declared in TestConfig.class

    @Autowired
    PilotMapper pilotMapper;    // bean declared in TestConfig.class

    @Mock
    RestClient restClient;

    @Mock
    PilotRepository repository;

    @Mock
    EveAssistUserRepository eveAssistUserRepository;

    PilotServiceImpl cut;

    @BeforeEach
    void setup() {
        cut = new PilotServiceImpl(mapper, repository, restClient, pilotMapper, eveAssistUserRepository);
    }

    @Test
    void contextLoads() {
        assertThat(cut).isNotNull();
    }
}