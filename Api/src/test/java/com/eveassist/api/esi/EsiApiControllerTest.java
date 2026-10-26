package com.eveassist.api.esi;

import com.eveassist.api.config.ApiConfig;
import com.eveassist.api.config.WebSecurityConfig;
import com.eveassist.api.sde.chr.ChrDao;
import com.eveassist.api.sde.chr.entity.ChrBloodline;
import com.eveassist.api.sde.chr.entity.ChrRace;
import com.eveassist.api.util.ReadFile;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureRestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({EsiApiController.class})
@AutoConfigureRestClient
@AutoConfigureMockRestServiceServer
@Import({WebSecurityConfig.class, ApiConfig.class, CharactersMapperImpl.class})
class EsiApiControllerTest {
    static final String ESI = "https://esi.evetech.net/latest";
    static final String USER_AGENT = "EveAssist/0.1 Where Am I";

    @Autowired
    MockMvc mvc;

    @Autowired
    MockRestServiceServer esi;

    @MockitoBean
    ChrDao chrDao;

    @MockitoBean
    AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver;


    @Test
    void givenValidPilotId_shouldReturnAllPublicData() throws Exception {
        ChrRace chrRace = ChrRace.builder().id(1).raceName("customRace").build();
        ChrBloodline chrBloodline = ChrBloodline.builder().id(1).bloodlineName("customBloodline").build();
        when(chrDao.getRace(anyInt())).thenReturn(chrRace);
        when(chrDao.getBloodline(anyInt())).thenReturn(chrBloodline);

        esi.expect(requestTo(ESI + "/characters/1/?datasource=tranquility"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess(fixture("response_characters_public.json"), MediaType.APPLICATION_JSON));
        esi.expect(requestTo(ESI + "/characters/1/portrait?datasource=tranquility"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture("response_characters_portrait.json"), MediaType.APPLICATION_JSON));
        esi.expect(requestTo(ESI + "/characters/affiliation?datasource=tranquility"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(content().json("[1]"))
                .andRespond(withSuccess(fixture("response_characters_affiliation.json"), MediaType.APPLICATION_JSON));
        esi.expect(requestTo(ESI + "/universe/names?datasource=tranquility"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[99011978, 98726945, 500002, 1272304578]"))
                .andRespond(withSuccess(fixture("response_universal_names.json"), MediaType.APPLICATION_JSON));

        mvc.perform(get("/character/1/public")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities())
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpectAll(jsonPath("$.allianceId", is(99011978)),
                        jsonPath("$.corporationId", is(98726945)),
                        jsonPath("$.factionId", is(500002)),
                        jsonPath("$.corporationDesc", is("Rage and Ruin")),
                        jsonPath("$.allianceDesc", is("Minmatar Fleet Alliance")),
                        jsonPath("$.factionDesc", is("Minmatar Republic")),
                        jsonPath("$.raceDesc", is("customRace")),
                        jsonPath("$.bloodlineDesc", is("customBloodline")));
        esi.verify();
    }

    @Test
    void givenValidPilotAndToken_shouldSendBearerTokenAndReturnNotifications() throws Exception {
        esi.expect(requestTo(ESI + "/characters/1/notifications?datasource=tranquility"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess(fixture("response_characters_notifications.json"),
                        MediaType.APPLICATION_JSON));

        mvc.perform(get("/character/1/notifications?accessToken=token")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities()))
                .andExpectAll(status().isOk(),
                        jsonPath("$[0].notification_id", is(1794469972)),
                        jsonPath("$[0].type", is("OrbitalReinforced")));
        esi.verify();
    }

    @Test
    void givenNoAuthToken_whenGetNotifications_thenThrow() throws Exception {
        mvc.perform(get("/character/1/notifications")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities()))
                .andExpectAll(status().is4xxClientError(),
                        status().reason("Required parameter 'accessToken' is not present."));
        esi.verify();
    }

    private static String fixture(String name) {
        return ReadFile.readFromFileToString("/esi/response/" + name);
    }
}
