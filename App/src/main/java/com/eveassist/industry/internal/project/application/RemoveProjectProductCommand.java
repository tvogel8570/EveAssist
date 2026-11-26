package com.eveassist.industry.internal.project.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.sde.TypeId;
import org.jmolecules.architecture.cqrs.annotation.Command;

@Command
public record RemoveProjectProductCommand(ProjectId project, TypeId type, long expectedVersion) {
}
