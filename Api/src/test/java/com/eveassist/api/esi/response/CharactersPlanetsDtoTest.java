package com.eveassist.api.esi.response;

import com.eveassist.api.util.ReadFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

//@Import({CharactersPlanetsDto.class})
class CharactersPlanetsDtoTest {
    static final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void givenValidCcpResponse_successParse() throws JacksonException {

        CharactersPlanetsDto[] planetsDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_planets.json"),
                CharactersPlanetsDto[].class
        );
        assertThat(planetsDto).isNotNull();
    }
}
