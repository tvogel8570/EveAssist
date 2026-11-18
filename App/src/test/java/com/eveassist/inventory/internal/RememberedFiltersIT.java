package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A list page that is opened with the menu's resume link comes back as it was left, in the same session and for the
 * same page. An address without a filter is still the list unfiltered.
 */
@AutoConfigureMockMvc
class RememberedFiltersIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    private Browser newBrowser() {
        var sub = newSub();
        viewerFor(sub);
        return new Browser(sub);
    }

    /** One browser of one user: the session cookie it was given, sent on with every request after. */
    private final class Browser {
        private final String sub;
        private Cookie session;

        Browser(String sub) {
            this.sub = sub;
        }

        ResultActions open(String address, String... header) throws Exception {
            var request = get(address).with(oidcLogin().idToken(token -> token.subject(sub))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER")));
            if (session != null) {
                request = request.cookie(session);
            }
            for (var i = 0; i + 1 < header.length; i += 2) {
                request = request.header(header[i], header[i + 1]);
            }
            var result = mvc.perform(request);
            var given = result.andReturn().getResponse().getCookie("SESSION");
            if (given != null) {
                session = given;
            }
            return result;
        }

        /** Where the menu's link to the page goes. */
        String sendsTo(String address) throws Exception {
            return open(address + "?resume=1").andExpect(status().is3xxRedirection()).andReturn().getResponse()
                    .getRedirectedUrl();
        }
    }

    @Test
    void inventoryComesBackWithTheFilterItWasLeftWith() throws Exception {
        var browser = newBrowser();

        // nothing was left yet, so the menu link goes to the page as it is, without the marker
        assertThat(browser.sendsTo("/inventory")).isEqualTo("/inventory");
        browser.open("/inventory?ui=1&chars=1&q=Tritanium&category=4&limit=300").andExpect(status().isOk());

        // the menu link now goes to the address the page had, and the list is on page one again
        var address = browser.sendsTo("/inventory");
        assertThat(address).startsWith("/inventory?").contains("q=Tritanium", "category=4", "chars=1")
                .doesNotContain("limit");
        browser.open(address).andExpect(status().isOk());
        // an address without a filter is still the unfiltered list: typed, bookmarked or reached with the Back button
        browser.open("/inventory").andExpect(status().isOk());

        // a filter changed later is the one that is kept, and so is a cleared one
        browser.open("/inventory?ui=1&chars=1&q=Pyerite").andExpect(status().isOk());
        assertThat(browser.sendsTo("/inventory")).contains("q=Pyerite").doesNotContain("Tritanium");
        browser.open("/inventory?ui=1&chars=1").andExpect(status().isOk());
        assertThat(browser.sendsTo("/inventory")).doesNotContain("q=");
    }

    @Test
    void blueprintsComeBackWithTheFilterTheyWereLeftWith() throws Exception {
        var browser = newBrowser();

        browser.open("/blueprints?q=Hauler&kind=BPC&tech=2&me=5&sort=me&dir=desc&limit=400").andExpect(status().isOk());

        var address = browser.sendsTo("/blueprints");
        assertThat(address).startsWith("/blueprints?").contains("q=Hauler", "kind=BPC", "tech=2", "me=5", "sort=me", "dir=desc")
                .doesNotContain("limit");

        // Clear goes to the page without a filter, which is the unfiltered list and is what is kept from then on
        browser.open(address).andExpect(status().isOk());
        browser.open("/blueprints").andExpect(status().isOk());
        assertThat(browser.sendsTo("/blueprints")).doesNotContain("Hauler", "kind=");
    }

    @Test
    void eachPageKeepsItsOwnFilterAndASessionKeepsNothingForAnother() throws Exception {
        var browser = newBrowser();
        browser.open("/inventory?ui=1&chars=1&q=Tritanium").andExpect(status().isOk());
        browser.open("/blueprints?q=Hauler&sort=type&dir=asc").andExpect(status().isOk());

        assertThat(browser.sendsTo("/inventory")).contains("q=Tritanium").doesNotContain("Hauler");
        assertThat(browser.sendsTo("/blueprints")).contains("q=Hauler").doesNotContain("Tritanium");

        // another session, even of the same user, starts with nothing
        var other = new Browser(browser.sub);
        assertThat(other.sendsTo("/inventory")).isEqualTo("/inventory");
        assertThat(other.sendsTo("/blueprints")).isEqualTo("/blueprints");
    }

    @Test
    void aRequestForTheListAloneIsNeverSentOnAndKeepsTheFilterToo() throws Exception {
        var browser = newBrowser();
        browser.open("/inventory?ui=1&chars=1&q=Tritanium").andExpect(status().isOk());
        browser.open("/blueprints?q=Hauler&sort=type&dir=asc").andExpect(status().isOk());

        // htmx asks for the list with the form's fields, and without any when the form is not on the page
        browser.open("/inventory", "HX-Request", "true").andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))));
        browser.open("/blueprints", "HX-Request", "true").andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))));
        // a list changed by htmx is the filter to come back to
        browser.open("/inventory?ui=1&chars=1&q=Pyerite", "HX-Request", "true").andExpect(status().isOk());
        assertThat(browser.sendsTo("/inventory")).contains("q=Pyerite");
    }

    @Test
    void theMenuLinksAskToResume() throws Exception {
        var browser = newBrowser();

        var page = browser.open("/inventory").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(page).containsPattern("id=\"nav-inventory\"[^>]*href=\"/inventory\\?resume=1\"")
                .containsPattern("id=\"nav-blueprints\"[^>]*href=\"/blueprints\\?resume=1\"");
    }
}
