package com.eveassist.fixture.industry.internal.web;

import com.eveassist.spike.industry.PlanId;
import com.eveassist.spike.industry.internal.application.PlanQueries;

/** Breaks the rule on purpose: shows bob's plan to anyone. */
public class BypassingController {
    private final PlanQueries queries;

    BypassingController(PlanQueries queries) {
        this.queries = queries;
    }

    Object editor(PlanId id) {
        return queries.editor(id);
    }
}
