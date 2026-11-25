package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.ProjectStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A project as a list shows it. The cost is the one that was stored, to the cent, and the list never recalculates it: a Draft's is its
 * latest costing, which may be out of date, and a Quoted project's is its latest quote's, which is frozen.
 *
 * @param deliveredCost null if the project was never costed
 * @param costedAt when that costing was made, or null
 * @param outOfDate true only for a Draft whose costing is older than the project's last edit, than the SDE, or than a facility it uses
 * @param latestQuoteNumber the number of the latest quote, or null if it has none
 * @param latestQuotePrice its price, or null
 * @param latestQuoteExpiresAt when it stops being open, or null
 */
public record ProjectOverview(
        ProjectId id, String name, ProjectStatus status, boolean archived, long productCount, int quoteCount,
        BigDecimal deliveredCost, Instant costedAt, boolean outOfDate, Integer latestQuoteNumber, BigDecimal latestQuotePrice,
        Instant latestQuoteExpiresAt) {
}
