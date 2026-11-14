package com.eveassist.identity.internal.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eveassist.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** What anyone may read without signing in: the privacy policy, security.txt, and the footer every page carries. */
@AutoConfigureMockMvc
class PublicPagesIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void thePrivacyPolicyIsOpenAndNamesTheOperatorAndTheContact() throws Exception {
        mvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vogel Development, LLC")))
                .andExpect(content().string(containsString("mailto:cass@vogelware.com")));
    }

    @Test
    void thePrivacyPolicySaysWhatItMustAboutDeletionBackupsAndWhereDataIsKept() throws Exception {
        mvc.perform(get("/privacy"))
                .andExpect(content().string(containsString("remain in a backup until it expires")))
                .andExpect(content().string(containsString("7 days")))
                .andExpect(content().string(containsString("28 days")))
                .andExpect(content().string(containsString("US West (Oregon)")))
                .andExpect(content().string(containsString("within one week")))
                .andExpect(content().string(containsString("read-only")));
    }

    @Test
    void thePrivacyPolicySaysWhenAndHowTheOperatorLooksAtDataForSupport() throws Exception {
        mvc.perform(get("/privacy"))
                .andExpect(content().string(containsString("id=\"support\"")))
                .andExpect(content().string(containsString("the refresh tokens and the sign-in sessions")))
                .andExpect(content().string(containsString("It cannot change or delete accounts, characters or plans")))
                .andExpect(content().string(containsString("recorded in AWS's")));
    }

    @Test
    void securityTxtIsPlainTextWithAContactAndAnExpiry() throws Exception {
        mvc.perform(get("/.well-known/security.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/plain")))
                .andExpect(content().string(containsString("Contact: mailto:cass@vogelware.com")))
                .andExpect(content().string(containsString("Expires: ")))
                .andExpect(content().string(containsString("Canonical: http://localhost/.well-known/security.txt")))
                .andExpect(content().string(containsString("/privacy#incidents")));
    }

    @Test
    void everyPageCarriesTheCcpNoticeTheOperatorAndTheLinks() throws Exception {
        for (var page : new String[] {"/", "/privacy", "/try"}) {
            mvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("© 2014 CCP hf. All rights reserved.")))
                    .andExpect(content().string(containsString(
                            "&quot;EVE&quot;, &quot;EVE Online&quot;, &quot;CCP&quot;, and all related logos and images are trademarks or registered trademarks of CCP hf.")))
                    .andExpect(content().string(containsString("is run by Vogel Development, LLC")))
                    .andExpect(content().string(containsString("id=\"footer-privacy\"")))
                    .andExpect(content().string(containsString("id=\"footer-security\"")))
                    .andExpect(content().string(containsString("build dev")));
        }
    }

    @Test
    void theHomePageSaysWhenAnAccountWasDeleted() throws Exception {
        mvc.perform(get("/").param("deleted", "done"))
                .andExpect(content().string(containsString("Your account and its data were deleted.")));
        mvc.perform(get("/").param("deleted", "partial"))
                .andExpect(content().string(containsString("your sign-in record remains")));
        mvc.perform(get("/").param("deleted", "<script>"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("id=\"deleted-notice\""))));
    }
}
