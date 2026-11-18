package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What a project costs and what to quote for it.
 *
 * @param jobs every job in build order, parents first
 * @param bought the items the project buys
 * @param lines each step of the cost, so the cost can be read from top to bottom
 * @param deliveredCost the sum of the lines
 * @param costPerUnit the delivered cost over all units delivered
 * @param totalSeconds the jobs' time added up, since one builder runs them one after another
 * @param longestJobSeconds the time of the single longest job, a stand-in for the time with several builder slots. It
 *         leaves out that a job waits for the parts it uses, so it is a lower bound and not a lead time
 * @param warnings gaps and shortfalls
 * @param quote the price in the project's mode, or null when the market price is the mode and a product has no price
 * @param comparison the price in the other mode, or null when it cannot be worked out
 */
@ValueObject
public record ProjectCost(
        List<ProjectJobCost> jobs, List<BoughtMaterial> bought, List<CostLine> lines, BigDecimal deliveredCost,
        BigDecimal costPerUnit, long totalSeconds, long longestJobSeconds, List<ProjectWarning> warnings, ProjectQuote quote,
        ProjectQuote comparison) {

    public ProjectCost {
        jobs = List.copyOf(jobs);
        bought = List.copyOf(bought);
        lines = List.copyOf(lines);
        warnings = List.copyOf(warnings);
    }

    /** A quote can be issued only when nothing is missing and there is a price. Notices do not block it. */
    public boolean quotable() {
        return warnings.stream().noneMatch(warning -> warning.kind().blocksQuote()) && quote != null;
    }

    public BigDecimal amount(CostLine.Component component) {
        return lines.stream().filter(line -> line.component() == component).map(CostLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
