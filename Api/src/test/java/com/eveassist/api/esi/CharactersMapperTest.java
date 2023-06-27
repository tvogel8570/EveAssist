package com.eveassist.api.esi;

import com.eveassist.api.esi.dto.PilotPublicDto;
import com.eveassist.api.esi.response.CharactersDto;
import com.eveassist.api.esi.response.CharactersPortraitDto;
import com.eveassist.api.util.ReadFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Import({CharactersMapperImpl.class})
class CharactersMapperTest {
    static final ObjectMapper mapper = new ObjectMapper();
    final CharactersMapper charactersMapper = new CharactersMapperImpl();

    @BeforeAll
    static void setup() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void givenNoAffiliation_shouldAcceptNull() throws JsonProcessingException {
        CharactersDto charactersDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_public.json"),
                CharactersDto.class);
        CharactersPortraitDto portraitDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_portrait.json"),
                CharactersPortraitDto.class);

        Throwable thrown = catchThrowable(() -> {
            PilotPublicDto dto = charactersMapper.from(charactersDto, portraitDto, null, null);
            assertThat(dto.name()).isEqualTo(charactersDto.name());
            assertThat(dto.alliance_id()).isEqualTo(charactersDto.alliance_id());
            assertThat(dto.corporation_id()).isEqualTo(charactersDto.corporation_id());
            assertThat(dto.px64x64()).isEqualTo(portraitDto.px64x64());

        });
        assertThat(thrown).isNull();
    }
}