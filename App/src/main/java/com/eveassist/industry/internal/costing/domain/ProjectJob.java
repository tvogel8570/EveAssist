package com.eveassist.industry.internal.costing.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * One job of a built item: its runs, the terms it is built under (its blueprint's ME and TE, the facility and the
 * builder) and what its blueprint costs for each run.
 *
 * @param blueprintCostPerRun the price per run of a bought BPC, or the depreciation per run of a BPO (0 for none)
 */
@ValueObject
public record ProjectJob(long runs, JobTerms terms, BigDecimal blueprintCostPerRun, BlueprintKind kind) {

    public ProjectJob {
        if (runs < 1) {
            throw new IllegalArgumentException("A job has at least one run, was " + runs);
        }
        if (terms == null || kind == null || blueprintCostPerRun == null || blueprintCostPerRun.signum() < 0) {
            throw new IllegalArgumentException("A job needs terms, a kind and a blueprint cost of 0 or more");
        }
    }
}
