package com.eveassist.industry.internal.application;

import org.jmolecules.architecture.cqrs.annotation.Command;

@Command
public record CreatePlanCommand(String name) {
}
