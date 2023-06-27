package com.eveassist.api.esi;

import com.eveassist.api.config.WebSecurityConfig;
import com.eveassist.api.esi.response.*;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(CharactersDto.class)))
                .thenReturn(ResponseEntity.of(Optional.of(charactersDto)));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(CharactersPortraitDto.class))).thenReturn(ResponseEntity.of(Optional.of(portraitDto)));
        when(restTemplate.postForObject(anyString(), anyList(), eq(CharactersAffiliationDto[].class)))
                .thenReturn(affiliationDto);
        when(restTemplate.postForObject(anyString(), anyList(), eq(UniverseNamesDto[].class))).thenReturn(namesDto);
        mvc.perform(get("/character/1/public")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities())
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpectAll(jsonPath("$.allianceId", is(99011978)),
                        jsonPath("$.corporationId", is(98726945)),
                        jsonPath("$.factionId", is(500002)),
                        jsonPath("$.corporationDesc", is("Rage and Ruin")),
                        jsonPath("$.allianceDesc", is("Minmatar Fleet Alliance")),
                        jsonPath("$.factionDesc", is("Minmatar Republic")));
    }

/*
    @Test
    void givenValidPilotAndToken_shouldReturnNotifications() throws Exception {
        CharactersNotificationsDto[] notificationsDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_notifications.json"),
                CharactersNotificationsDto[].class);
        MvcResult mvcResult = mvc.perform(get("/character/1/notifications?accessToken=token")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities()))
                .andExpect(status().isOk())
                .andReturn();
        CharactersNotificationsDto[] results = mapper.readValue(mvcResult.getResponse().getContentAsString(),
                CharactersNotificationsDto[].class);
        assertThat(results).hasSizeGreaterThan(0);
        assertThat(results[0].)
    }
*/

    @Test
    void givenNoAuthToken_whenGetNotifications_thenThrow() throws Exception {
        CharactersNotificationsDto[] notificationsDto = mapper.readValue(
                ReadFile.readFromFileToString("/esi/response/response_characters_notifications.json"),
                CharactersNotificationsDto[].class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(CharactersNotificationsDto[].class))).thenReturn(ResponseEntity.of(Optional.of(notificationsDto)));
        mvc.perform(get("/character/1/notifications")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities()))
                .andExpectAll(status().is4xxClientError(),
                        status().reason("Required parameter 'accessToken' is not present."));
    }
}