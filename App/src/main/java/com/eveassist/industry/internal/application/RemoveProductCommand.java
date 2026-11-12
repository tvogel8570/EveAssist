package com.eveassist.industry.internal.application;

import com.eveassist.industry.PlanId;
import com.eveassist.sde.TypeId;
import org.jmolecules.architecture.cqrs.annotation.Command;

/** Takes a product out of a plan, if the plan is still at the version the user saw. */
@Command
public record RemoveProductCommand(PlanId plan, TypeId type, long expectedVersion) {
}
