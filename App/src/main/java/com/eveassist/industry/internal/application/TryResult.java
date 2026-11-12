package com.eveassist.industry.internal.application;

import java.util.List;

/**
 * A plan without an account, costed: the plan as the page shows it, the state the browser keeps, the items that could
 * not be priced (and why none was fetched), whether the prices are old, an optional notice, and what a removal took out.
 */
public record TryResult(
        PlanDetails plan,
        String state,
        List<String> unpriced,
        boolean stale,
        String notice,
        ProductRemoval removed) {

    public TryResult {
        unpriced = List.copyOf(unpriced);
    }
}
