package com.eveassist.industry.internal.domain;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * One item in a costed plan. A buildable item has a blueprint, and may still be bought. A built item has no price of its
 * own, and a bought item may have no known price.
 */
@ValueObject
public record LineCost(
        TypeId type, long quantity, boolean buildable, boolean built, BigDecimal unitPrice, BigDecimal cost) {
}
