package com.eveassist.api.esi.pi.impl;

import com.eveassist.api.esi.pi.PlanetaryIndustryService;
import com.eveassist.api.esi.pi.dto.PlanetPiDto;
import com.eveassist.api.esi.response.CharactersPlanetsDetailDto;
import com.eveassist.api.esi.response.CharactersPlanetsDto;
import com.eveassist.api.sde.pi.PiDao;
import com.eveassist.api.sde.pi.impl.PiDaoImpl;
import com.eveassist.api.util.ReadFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlanetaryIndustryServiceImpl.class, PiDaoImpl.class})
class PlanetaryIndustryServiceImplTest {
    @Autowired
    PlanetaryIndustryService cut;
    @Autowired
    PiDao piDao;
    static final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void init() {
        cut = new PlanetaryIndustryServiceImpl(piDao);
    }

    @Test
    void contextLoads() {
        assertThat(cut).isNotNull();
        assertThat(piDao).isNotNull();
    }

    @Test
    void givenPlanetWithExtractor_whenSummarize_thenCorrectValues() throws JacksonException {
        CharactersPlanetsDto[] charactersPlanetsDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_planets.json"),
                CharactersPlanetsDto[].class);
        CharactersPlanetsDetailDto charactersPlanetsDetailDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_planets_detail_extractor.json"),
                CharactersPlanetsDetailDto.class);
        PlanetPiDto summarizedPlanet = cut.summarizePlanet(charactersPlanetsDto[0], charactersPlanetsDetailDto);
        assertThat(summarizedPlanet).isNotNull();
        assertThat(summarizedPlanet.factories()).hasSize(8);
        assertThat(summarizedPlanet.extractors()).hasSize(1);
        assertThat(summarizedPlanet.storage()).hasSize(3);
        assertThat(summarizedPlanet.products()).hasSize(18);
        assertThat(summarizedPlanet.piClassification()).isEqualTo("P1 extraction");
    }
}