package com.eveassist.characters.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * An admin account is separate from the features: it has no linked characters and reads no EVE data (see users.md). The
 * navigation hides the links, but only the server can refuse, so each address of the character and inventory pages is
 * tried with an admin session and must answer 403.
 */
@AutoConfigureMockMvc
class AdminAccessIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    private RequestPostProcessor admin(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void anAdminCannotUseTheCharacterOrInventoryPages() throws Exception {
        var sub = newSub();
        viewerFor(sub);
        var id = UUID.randomUUID();

        mvc.perform(get("/characters").with(admin(sub))).andExpect(status().isForbidden());
        mvc.perform(get("/characters/link").with(admin(sub))).andExpect(status().isForbidden());
        mvc.perform(get("/characters/link/callback").param("code", "c").param("state", "s").with(admin(sub)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/characters/{id}/portrait", id).with(admin(sub))).andExpect(status().isForbidden());
        mvc.perform(post("/characters/features/INVENTORY").param("on", "true").with(admin(sub)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/characters/{id}/remove", id).with(admin(sub)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/characters/{id}/drop-access", id).with(admin(sub)).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/inventory").with(admin(sub))).andExpect(status().isForbidden());
        mvc.perform(get("/inventory/status").with(admin(sub))).andExpect(status().isForbidden());
        mvc.perform(get("/inventory/filter/category").with(admin(sub))).andExpect(status().isForbidden());
        mvc.perform(get("/inventory/item/34").with(admin(sub))).andExpect(status().isForbidden());
        mvc.perform(get("/inventory/export.csv").with(admin(sub))).andExpect(status().isForbidden());
        mvc.perform(post("/inventory/refresh").with(admin(sub)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void aUserWithTheUserRoleStillGetsThroughSoTheTestProvesTheRoleIsWhatDecides() throws Exception {
        var sub = newSub();
        viewerFor(sub);

        mvc.perform(get("/characters").with(oidcLogin().idToken(token -> token.subject(sub))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());
    }
}
