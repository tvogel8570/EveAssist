package com.eveassist.industry.internal.domain;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * One item in a costed plan. A buildable item has a blueprint, and may still be bought. A built item has no price of its
 * own, and a bought item may have no known price. A built item also has the runs its blueprint needs, the units those
 * runs make and the seconds they take (all three are 0 for an item that is bought).
 */
@ValueObject
public record LineCost(
        TypeId type, long quantity, boolean buildable, boolean built, BigDecimal unitPrice, BigDecimal cost,
        long runs, long unitsMade, long seconds) {

    /** An item that is bought, so it has no runs. */
    public static LineCost bought(
            TypeId type, long quantity, boolean buildable, BigDecimal unitPrice, BigDecimal cost) {
        return new LineCost(type, quantity, buildable, false, unitPrice, cost, 0, 0, 0);
    }
}
