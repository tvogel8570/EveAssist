package com.eveassist.industry.internal.domain;

import com.eveassist.industry.internal.costing.domain.BlueprintKind;
import com.eveassist.industry.internal.costing.domain.JobCost;
import com.eveassist.industry.internal.costing.domain.JobTerms;
import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * One job of the project and the runs it was given, with the blueprint cost for those runs, and what it was costed under: the blueprint's
 * ME and TE, the facility and the builder (the terms), and what kind of blueprint it was. The terms are what a costing trace shows.
 */
@ValueObject
public record ProjectJobCost(JobCost job, BigDecimal blueprintCost, JobTerms terms, BlueprintKind kind) {
}
