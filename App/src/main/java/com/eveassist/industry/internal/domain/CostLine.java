package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/** One step of the delivered cost with its amount. The surplus credit is negative. */
@ValueObject
public record CostLine(Component component, BigDecimal amount) {

    /** The steps in the order the page shows them. */
    public enum Component { MATERIALS, INSTALL_COST, BLUEPRINT, TIME_VALUE, HAULING, CONTRACT_FEE, SURPLUS_CREDIT }
}
