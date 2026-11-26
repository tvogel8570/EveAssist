package com.eveassist.industry.internal.project.costing.application;

import java.util.List;

/**
 * The costing a project page shows. A Draft shows its own latest costing, which can be out of date and says why. A Quoted project shows its
 * latest quote's, which is frozen: it is what was sent, and it is never out of date.
 *
 * @param quoteNumber the quote it belongs to, or 0 for a Draft's
 * @param outOfDate why a Draft's costing may be out of date, empty if it is current or frozen
 */
public record CostingView(StoredCosting costing, boolean frozen, int quoteNumber, List<CostingStaleness> outOfDate) {

    public CostingView {
        outOfDate = List.copyOf(outOfDate);
    }

    public boolean current() {
        return outOfDate.isEmpty();
    }
}
