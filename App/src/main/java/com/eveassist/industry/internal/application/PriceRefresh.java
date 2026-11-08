package com.eveassist.industry.internal.application;

import java.util.List;

/**
 * The plan after a price refresh, and the names of the items ESI could not be asked about. Those keep the price the
 * plan already had.
 */
public record PriceRefresh(PlanDetails plan, List<String> failedItems) {

    public PriceRefresh {
        failedItems = List.copyOf(failedItems);
    }
}
