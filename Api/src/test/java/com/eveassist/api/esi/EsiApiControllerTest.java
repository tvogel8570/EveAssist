package com.eveassist.api.esi;

import com.eveassist.api.config.WebSecurityConfig;
import com.eveassist.api.esi.response.CharactersAffiliationDto;
import com.eveassist.api.esi.response.CharactersDto;
import com.eveassist.api.esi.response.CharactersPortraitDto;
import com.eveassist.api.esi.response.UniverseNamesDto;
import com.eveassist.api.util.ReadFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({EsiApiController.class})
@Import({WebSecurityConfig.class, CharactersMapperImpl.class})
class EsiApiControllerTest {
    static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    CharactersMapper charactersMapper;

    @MockBean
    RestTemplate restTemplate;

    @MockBean
    AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver;

    @BeforeAll
    static void setup() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void givenValidPilotId_shouldReturnAllPublicData() throws Exception {
        CharactersDto charactersDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_public.json"),
                CharactersDto.class);
        CharactersPortraitDto portraitDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_portrait.json"),
                CharactersPortraitDto.class);
        CharactersAffiliationDto[] affiliationDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_affiliation.json"),
                CharactersAffiliationDto[].class);
        UniverseNamesDto[] namesDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_universal_names.json"),
                UniverseNamesDto[].class);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CharactersDto.class))).thenReturn(ResponseEntity.of(Optional.of(charactersDto)));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CharactersPortraitDto.class))).thenReturn(ResponseEntity.of(Optional.of(portraitDto)));
        when(restTemplate.postForObject(anyString(), anyList(), eq(CharactersAffiliationDto[].class))).thenReturn(affiliationDto);
        when(restTemplate.postForObject(anyString(), anyList(), eq(UniverseNamesDto[].class))).thenReturn(namesDto);
        mvc.perform(get("/character/1/public")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("NICE"), new SimpleGrantedAuthority("AUTHOR")))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpectAll(jsonPath("$.alliance_id", is(99011978)),
                        jsonPath("$.corporation_id", is(98726945)),
                        jsonPath("$.faction_id", is(500002)),
                        jsonPath("$.corporation_desc", is("Rage and Ruin")),
                        jsonPath("$.alliance_desc", is("Minmatar Fleet Alliance")),
                        jsonPath("$.faction_desc", is("Minmatar Republic")))
                .andDo(print());
    }
}