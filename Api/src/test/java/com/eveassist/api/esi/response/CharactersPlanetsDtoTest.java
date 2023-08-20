package com.eveassist.api.esi.response;

import com.eveassist.api.util.ReadFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

//@Import({CharactersPlanetsDto.class})
class CharactersPlanetsDtoTest {
    static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void setup() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void givenValidCcpResponse_successParse() throws JsonProcessingException {

        CharactersPlanetsDto[] planetsDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_planets.json"),
                CharactersPlanetsDto[].class
        );
        assertThat(planetsDto).isNotNull();
    }
}
