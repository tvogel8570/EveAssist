package com.eveassist.spike;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Same source in both options: the two options must behave the same from outside. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BehaviorTest {
    private static final String ALICE_PLAN = "11111111-1111-1111-1111-111111111111";
    private static final String ALICE_LINE = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String BOB_PLAN = "33333333-3333-3333-3333-333333333333";
    private static final String BOB_LINE = "bbbbbbbb-0000-0000-0000-000000000001";

    @Autowired
    WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void listScreenShowsOwnPlansWithTotals() throws Exception {
        mvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rifter batch")))
                .andExpect(content().string(containsString("5005000")))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void editorScreenRenders() throws Exception {
        mvc.perform(get("/plans/" + ALICE_PLAN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tritanium")))
                .andExpect(content().string(containsString("hx-post=\"/plans/" + ALICE_PLAN)));
    }

    @Test
    void changingQuantityReturnsOnlyTheChangedRowAndTotal() throws Exception {
        mvc.perform(post("/plans/" + ALICE_PLAN + "/lines/" + ALICE_LINE + "/quantity").param("quantity", "20"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rifter")))
                .andExpect(content().string(containsString("hx-swap-oob=\"true\"")))
                .andExpect(content().string(containsString("10005000")))
                .andExpect(content().string(not(containsString("Tritanium"))))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void screenChangeIsVisibleThroughTheApi() throws Exception {
        mvc.perform(post("/plans/" + ALICE_PLAN + "/lines/" + ALICE_LINE + "/quantity").param("quantity", "20"));
        mvc.perform(get("/api/v1/plans/" + ALICE_PLAN + "/costing"))
                .andExpect(jsonPath("$.totalCost").value(10_005_000));
    }

    @Test
    void invalidQuantityIsRejected() throws Exception {
        mvc.perform(post("/plans/" + ALICE_PLAN + "/lines/" + ALICE_LINE + "/quantity").param("quantity", "0"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void pagesHideOtherAccountsPlans() throws Exception {
        mvc.perform(get("/plans/" + BOB_PLAN)).andExpect(status().isNotFound());
        mvc.perform(post("/plans/" + BOB_PLAN + "/lines/" + BOB_LINE + "/quantity").param("quantity", "2"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/plans/" + BOB_PLAN + "/costing").header("X-Viewer", "bob"))
                .andExpect(jsonPath("$.totalCost").value(2_000_000_000));
    }

    @Test
    void apiHidesOtherAccountsPlans() throws Exception {
        mvc.perform(get("/api/v1/plans")).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/v1/plans/" + BOB_PLAN)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/plans/" + BOB_PLAN + "/costing")).andExpect(status().isNotFound());
    }

    @Test
    void apiResourcesDoNotCarryScreenFields() throws Exception {
        mvc.perform(get("/api/v1/plans"))
                .andExpect(jsonPath("$[0].totalCost").doesNotExist());
        mvc.perform(get("/api/v1/plans/" + ALICE_PLAN))
                .andExpect(jsonPath("$.lines[0].lineCost").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void etagAnswersNotModifiedUntilThePlanChanges() throws Exception {
        String etag = mvc.perform(get("/api/v1/plans/" + ALICE_PLAN))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(get("/api/v1/plans/" + ALICE_PLAN).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
        mvc.perform(post("/plans/" + ALICE_PLAN + "/lines/" + ALICE_LINE + "/quantity").param("quantity", "20"));
        mvc.perform(get("/api/v1/plans/" + ALICE_PLAN).header("If-None-Match", etag))
                .andExpect(status().isOk());
    }
}
