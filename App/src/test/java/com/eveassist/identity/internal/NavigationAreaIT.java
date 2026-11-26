package com.eveassist.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.eveassist.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** Every page says which menu entry it belongs to: the entry is marked as the current page, and the page carries the area's name for its colour. */
@AutoConfigureMockMvc
class NavigationAreaIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    private String page(String path) throws Exception {
        var sub = newSub();
        accounts.signedIn(sub, "Test Pilot");
        return mvc.perform(get(path).with(oidcLogin().idToken(token -> token.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andReturn().getResponse().getContentAsString();
    }

    private static List<String> current(String html) {
        return Pattern.compile("<a[^>]*id=\"(nav-[a-z-]+)\"[^>]*aria-current=\"page\"").matcher(html).results().map(found -> found.group(1)).toList();
    }

    @Test
    void eachPageMarksItsOwnMenuEntryAndNoOther() throws Exception {
        var pages = Map.of(
                "/plans", "plans nav-plans", "/projects", "projects nav-projects", "/project-defaults", "projects nav-projects",
                "/facilities", "facilities nav-facilities", "/market/blueprint-copies", "blueprint-copies nav-bpc",
                "/inventory", "inventory nav-inventory", "/blueprints", "blueprints nav-blueprints", "/characters", "characters nav-characters");
        for (var expected : pages.entrySet()) {
            var html = page(expected.getKey());
            var area = expected.getValue().split(" ")[0];
            var entry = expected.getValue().split(" ")[1];

            assertThat(current(html)).as(expected.getKey()).containsExactly(entry);
            assertThat(html).as(expected.getKey()).containsPattern("<body[^>]*data-area=\"" + area + "\"");
        }
    }

    @Test
    void aPageOfNoMenuEntryMarksNone() throws Exception {
        var html = page("/account");

        assertThat(current(html)).isEmpty();
        assertThat(html).doesNotContain("data-area=");
    }

    @Test
    void tryAPlanIsItsOwnEntryForAVisitor() throws Exception {
        var html = mvc.perform(get("/try")).andReturn().getResponse().getContentAsString();

        assertThat(current(html)).containsExactly("nav-try");
        assertThat(html).containsPattern("<body[^>]*data-area=\"plans\"");
    }
}
