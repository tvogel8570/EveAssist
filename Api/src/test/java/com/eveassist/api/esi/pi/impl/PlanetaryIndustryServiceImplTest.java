package com.eveassist.api.esi.pi.impl;

import com.eveassist.api.esi.pi.PlanetaryIndustryService;
import com.eveassist.api.esi.pi.dto.PlanetPiDto;
import com.eveassist.api.esi.response.CharactersPlanetsDetailDto;
import com.eveassist.api.util.ReadFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanetaryIndustryServiceImplTest {
    PlanetaryIndustryService cut;
    static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void setup() {
        mapper.registerModule(new JavaTimeModule());
    }

    @BeforeEach
    void init() {
        cut = new PlanetaryIndustryServiceImpl();
    }

    @Test
    void contextLoads() {
        assertThat(cut).isNotNull();
    }

    @Test
    void givenPlanetWithExtractor_whenSummarize_thenCorrectValues() throws JsonProcessingException {
        CharactersPlanetsDetailDto charactersPlanetsDetailDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_planets_detail_extractor.json"),
                CharactersPlanetsDetailDto.class);
        PlanetPiDto summarizedPlanet = cut.summarizePlanet(charactersPlanetsDetailDto);
        assertThat(summarizedPlanet).isNotNull();
    }
}