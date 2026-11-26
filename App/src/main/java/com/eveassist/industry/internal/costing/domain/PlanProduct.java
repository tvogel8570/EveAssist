package com.eveassist.industry.internal.costing.domain;

import com.eveassist.sde.TypeId;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record PlanProduct(TypeId type, long quantity) {
}
