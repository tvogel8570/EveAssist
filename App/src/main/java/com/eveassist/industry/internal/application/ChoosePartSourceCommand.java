package com.eveassist.industry.internal.application;

import com.eveassist.industry.PlanId;
import com.eveassist.sde.TypeId;
import org.jmolecules.architecture.cqrs.annotation.Command;

@Command
public record ChoosePartSourceCommand(PlanId plan, TypeId part, boolean buy, long expectedVersion) {
}
