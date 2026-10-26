package com.eveassist.api.esi.response;

import com.eveassist.api.util.ReadFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CharactersPlanetsDetailDtoTest {
    static final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void givenValidCcpResponse_successParseExtractor() throws JacksonException {
        CharactersPlanetsDetailDto charactersPlanetsDetailDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_planets_detail_extractor.json"),
                CharactersPlanetsDetailDto.class);
        Assertions.assertThat(charactersPlanetsDetailDto).isNotNull();
    }

    @Test
    void givenValidCcpResponse_successParseFactory() throws JacksonException {
        CharactersPlanetsDetailDto charactersPlanetsDetailDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_planets_detail_factory.json"),
                CharactersPlanetsDetailDto.class);
        Assertions.assertThat(charactersPlanetsDetailDto).isNotNull();
    }
}