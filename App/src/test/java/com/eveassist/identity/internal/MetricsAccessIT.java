package com.eveassist.identity.internal;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The metrics the concurrency runbook watches (database pool, fetch queue) are for an admin only. Nothing else of the
 * actuator is exposed.
 */
@AutoConfigureMockMvc
class MetricsAccessIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    private RequestPostProcessor signedIn(String sub, String role) {
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority(role));
    }

    @Test
    void anAdminReadsThePoolAndFetchMetrics() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/actuator/metrics/hikaricp.connections.pending").with(signedIn(sub, "ROLE_ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("hikaricp.connections.pending")));
        mvc.perform(get("/actuator/metrics/hikaricp.connections.active").with(signedIn(sub, "ROLE_ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics/eveassist.asset.fetch.waiting").with(signedIn(sub, "ROLE_ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics/eveassist.asset.fetch.turnaround").with(signedIn(sub, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void anOrdinaryUserAndASignedOutVisitorCannotReadThem() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/actuator/metrics").with(signedIn(sub, "ROLE_USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics/hikaricp.connections.pending").with(signedIn(sub, "ROLE_USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics")).andExpect(status().is3xxRedirection());
    }

    @Test
    void nothingBeyondMetricsIsExposedEvenToAnAdmin() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        for (var path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/health",
                "/actuator/configprops"}) {
            mvc.perform(get(path).with(signedIn(sub, "ROLE_ADMIN"))).andExpect(status().isNotFound());
        }
    }
}
