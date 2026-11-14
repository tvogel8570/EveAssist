package com.eveassist.identity.internal.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.eveassist.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** An image built for a release shows the release version before the commit id: {@code build 1.2.3 (abc1234)}. */
// Its own properties make it a context of its own, and every cached context keeps a connection pool open on the one test Postgres, whose
// connections run out near the end of the run (the browser tests then fail to start). Closing this context after the class gives them back.
@DirtiesContext
@AutoConfigureMockMvc
@TestPropertySource(properties = {"eveassist.build.version=1.2.3", "eveassist.build.commit=abc1234"})
class FooterVersionIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void theFooterShowsTheReleaseVersionAndTheCommit() throws Exception {
        mvc.perform(get("/privacy"))
                .andExpect(content().string(containsString("build 1.2.3 (abc1234)")));
    }
}
