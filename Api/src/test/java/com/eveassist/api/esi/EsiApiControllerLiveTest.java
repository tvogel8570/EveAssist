package com.eveassist.api.esi;

import com.eveassist.api.config.ApiConfig;
import com.eveassist.api.config.WebSecurityConfig;
import com.eveassist.api.sde.chr.ChrDao;
import com.eveassist.api.sde.chr.entity.ChrBloodline;
import com.eveassist.api.sde.chr.entity.ChrRace;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureRestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Calls the real, public ESI endpoints through the controller's RestClient, so it needs network access. It runs in
 * every build; to skip it offline, run {@code ./mvnw verify -DexcludedGroups=esi-live}.
 * <p>
 * Assertions stick to data that can't change for this character (name, birthday, race); corporation and alliance
 * membership can, so only their presence is checked.
 */
@Tag("esi-live")
@WebMvcTest({EsiApiController.class})
@AutoConfigureRestClient
@Import({WebSecurityConfig.class, ApiConfig.class, CharactersMapperImpl.class})
class EsiApiControllerLiveTest {
    static final long PILOT_ID = 1272304578L;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ChrDao chrDao;

    @MockitoBean
    AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver;

    @Test
    void givenRealPilotId_shouldReturnPublicDataFromEsi() throws Exception {
        when(chrDao.getRace(anyInt())).thenReturn(ChrRace.builder().id(1).raceName("customRace").build());
        when(chrDao.getBloodline(anyInt()))
                .thenReturn(ChrBloodline.builder().id(11).bloodlineName("customBloodline").build());

        mvc.perform(get("/character/{pilotId}/public", PILOT_ID)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities())
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpectAll(jsonPath("$.name", is("Kim Huang")),
                        jsonPath("$.birthday", is("08-18-2008 23:17")),
                        jsonPath("$.raceId", is(1)),
                        jsonPath("$.corporationId").isNumber(),
                        jsonPath("$.corporationDesc", not(blankOrNullString())),
                        jsonPath("$.px64x64", startsWith("https://images.evetech.net/")));
        verify(chrDao, atLeastOnce()).getRace(1);
    }
}
