package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/** One job of the project and the runs it was given, with the blueprint cost for those runs. */
@ValueObject
public record ProjectJobCost(JobCost job, BigDecimal blueprintCost) {
}
