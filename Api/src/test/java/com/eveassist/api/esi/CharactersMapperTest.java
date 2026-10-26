package com.eveassist.api.esi;

import com.eveassist.api.esi.dto.PilotPublicDto;
import com.eveassist.api.esi.response.CharactersDto;
import com.eveassist.api.esi.response.CharactersPortraitDto;
import com.eveassist.api.util.ReadFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Import({CharactersMapperImpl.class})
class CharactersMapperTest {
    static final JsonMapper mapper = JsonMapper.builder().build();
    final CharactersMapper charactersMapper = new CharactersMapperImpl();

    @Test
    void givenNoAffiliation_shouldAcceptNull() throws JacksonException {
        CharactersDto charactersDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_public.json"),
                CharactersDto.class);
        CharactersPortraitDto portraitDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_portrait.json"),
                CharactersPortraitDto.class);

        Throwable thrown = catchThrowable(() -> {
            PilotPublicDto dto = charactersMapper.from(charactersDto, portraitDto, null, null);
            assertThat(dto.name()).isEqualTo(charactersDto.name());
            assertThat(dto.allianceId()).isEqualTo(charactersDto.allianceId());
            assertThat(dto.corporationId()).isEqualTo(charactersDto.corporationId());
            assertThat(dto.px64x64()).isEqualTo(portraitDto.px64x64());

        });
        assertThat(thrown).isNull();
    }
}