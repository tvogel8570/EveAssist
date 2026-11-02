package com.eveassist.industry.internal.domain;

import com.eveassist.sde.TypeId;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record PlanProduct(TypeId type, long quantity) {
}
