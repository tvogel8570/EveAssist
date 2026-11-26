package com.eveassist.industry.internal.costing.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A blueprint the developer has that can make an item.
 *
 * @param runsLeft the runs a copy has left, ignored for an original, which has no limit
 * @param costPerRun the price per run of a bought copy, or the depreciation per run of an original (0 for none)
 */
@ValueObject
public record OwnedBlueprint(BlueprintKind kind, int me, int te, long runsLeft, BigDecimal costPerRun) {

    public OwnedBlueprint {
        if (kind == null || kind == BlueprintKind.TYPED) {
            throw new IllegalArgumentException("An owned blueprint is an original or a copy");
        }
        if (me < 0 || me > 10 || te < 0 || te > 20 || runsLeft < 0 || costPerRun == null || costPerRun.signum() < 0) {
            throw new IllegalArgumentException("ME is 0 to 10, TE 0 to 20, runs and cost are 0 or more");
        }
    }
}
