package com.eveassist.industry.internal.project.costing.domain;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/** An item the project buys. An item without a price has no unit price and no cost, and is also a warning. */
@ValueObject
public record BoughtMaterial(TypeId type, long quantity, BigDecimal unitPrice, BigDecimal cost) {
}
