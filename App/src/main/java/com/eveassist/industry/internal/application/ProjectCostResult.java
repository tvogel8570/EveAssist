package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.ProjectCost;
import com.eveassist.industry.internal.domain.ProjectWarning;
import com.eveassist.industry.internal.domain.SplitComparison;
import java.time.Instant;
import java.util.List;

/**
 * A project costed, with what a stored costing needs to say when it is out of date.
 *
 * @param costedProjectVersion the project's edit version this was costed for
 * @param sdeVersion the SDE import the recipes came from
 * @param pricesAsOf the age of the oldest price among what is bought, or null if nothing is
 * @param adjustedPricesAsOf when the adjusted prices, which the install costs are worked out from, were last known to be ESI's current
 *         copy, or null if they never were
 * @param comparison the costing under the project's split policies next to the same project at least cost
 * @param warnings every warning, including the ones the use case adds to the costing's own (an item with no facility)
 */
public record ProjectCostResult(
        ProjectId project, long costedProjectVersion, String sdeVersion, Instant pricesAsOf, Instant adjustedPricesAsOf,
        SplitComparison comparison, List<ProjectWarning> warnings) {

    public ProjectCostResult {
        warnings = List.copyOf(warnings);
    }

    /** The costing under the project's own policies. */
    public ProjectCost cost() {
        return comparison.chosen();
    }

    /** A quote can be issued only when nothing is missing and there is a price. Notices do not block it. */
    public boolean quotable() {
        return cost().quote() != null && warnings.stream().noneMatch(warning -> warning.kind().blocksQuote());
    }
}
