package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.sde.TypeId;
import org.jmolecules.architecture.cqrs.annotation.Command;

@Command
public record ChangeProjectQuantityCommand(ProjectId project, TypeId type, long quantity, long expectedVersion) {
}
