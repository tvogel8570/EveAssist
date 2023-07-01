package com.eveassist.client.pilot.impl;

import com.eveassist.client.TestConfig;
import com.eveassist.client.pilot.PilotMapper;
import com.eveassist.client.pilot.PilotRepository;
import com.eveassist.client.pilot.dto.PilotListInfo;
import com.eveassist.client.pilot.dto.PilotPublicDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SpringJUnitConfig({TestConfig.class})
class PilotServiceImplTest {
    @Autowired
    ObjectMapper mapper;    // bean declared in TestConfig.class

    @Autowired
    PilotMapper pilotMapper;    // bean declared in TestConfig.class

    @Mock
    RestTemplate restTemplate;

    @Mock
    PilotRepository repository;

    PilotServiceImpl cut;

    @BeforeEach
    void setup() {
        cut = new PilotServiceImpl(mapper, repository, restTemplate, pilotMapper);
    }

    @Test
    void contextLoads() {
        assertThat(cut).isNotNull();
    }

    @Test
    void whenValidEvePilotId_thenPublicDataRetrievedFromApi() {
        List<PilotListInfo> data = List.of(new PilotListInfo("asdf", 1272304578L, "Kim Huang", "qwer"));
        when(repository.findByEveAssistUser_UniqueUserOrderByNameAsc(
                any(UUID.class), any(Pageable.class))).thenReturn(data);

        PilotPublicDto faction = PilotPublicDto.builder().factionDesc("faction").build();
        ResponseEntity<PilotPublicDto> responseEntity = new ResponseEntity<>(faction, HttpStatus.ACCEPTED);
        when(restTemplate.getForEntity(any(URI.class), eq(PilotPublicDto.class))).thenReturn(responseEntity);

        List<PilotListInfo> pilotsWithDetailsForUser = cut.getPilotsWithDetailsForUser(UUID.randomUUID(), "");
        assertThat(pilotsWithDetailsForUser).isNotNull().hasSize(1);
        assertThat(pilotsWithDetailsForUser.get(0).getName()).isEqualTo(data.get(0).getName());
        assertThat(pilotsWithDetailsForUser.get(0).getFactionDesc()).isEqualTo(faction.factionDesc());
    }
}