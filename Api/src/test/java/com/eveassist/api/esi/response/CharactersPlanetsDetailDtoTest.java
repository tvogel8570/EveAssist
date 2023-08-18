package com.eveassist.api.esi.response;

import com.eveassist.api.util.ReadFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CharactersPlanetsDetailDtoTest {
    static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void setup() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void givenValidCcpResponse_successParseExtractor() throws JsonProcessingException {
        CharactersPlanetsDetailDto charactersPlanetsDetailDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_planets_detail_extractor.json"),
                CharactersPlanetsDetailDto.class);
        Assertions.assertThat(charactersPlanetsDetailDto).isNotNull();
    }

    @Test
    void givenValidCcpResponse_successParseFactory() throws JsonProcessingException {
        CharactersPlanetsDetailDto charactersPlanetsDetailDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_planets_detail_factory.json"),
                CharactersPlanetsDetailDto.class);
        Assertions.assertThat(charactersPlanetsDetailDto).isNotNull();
    }
}